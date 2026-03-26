package com.example.demo.controller;

import com.example.demo.config.PlatformConfigProperties;
import com.example.demo.mapper.CustomerOrderQueryMapper;
import com.example.demo.mapper.PaymentLogQueryMapper;
import com.example.demo.model.CustomerOrder;
import com.example.demo.model.PaymentLog;
import com.example.demo.model.User;
import com.example.demo.service.AlipayPaymentSyncService;
import com.example.demo.util.AlipaySigner;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payments/alipay")
@CrossOrigin(origins = "*")
public class AlipayController {

    private static final String BATCH_TRADE_PREFIX = "PAYB_";
    private static final String LOG_TYPE_BATCH = "BATCH";
    private static final String BATCH_REMARK_PREFIX = "ORDER_IDS:";

    private final PlatformConfigProperties platformConfigProperties;
    private final CustomerOrderQueryMapper customerOrderQueryMapper;
    private final PaymentLogQueryMapper paymentLogQueryMapper;
    private final AlipayPaymentSyncService alipayPaymentSyncService;

    public AlipayController(PlatformConfigProperties platformConfigProperties,
                            CustomerOrderQueryMapper customerOrderQueryMapper,
                            PaymentLogQueryMapper paymentLogQueryMapper,
                            AlipayPaymentSyncService alipayPaymentSyncService) {
        this.platformConfigProperties = platformConfigProperties;
        this.customerOrderQueryMapper = customerOrderQueryMapper;
        this.paymentLogQueryMapper = paymentLogQueryMapper;
        this.alipayPaymentSyncService = alipayPaymentSyncService;
    }

    @PostMapping("/pay")
    public Map<String, Object> pay(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User user = currentUser(request);
        List<CustomerOrder> orders = resolvePayOrders(body, user);

        validateAlipayConfig(platformConfigProperties);

        String gateway = isBlank(platformConfigProperties.getAlipayGateway())
                ? "https://openapi-sandbox.dl.alipaydev.com/gateway.do"
                : platformConfigProperties.getAlipayGateway();

        Map<String, String> params = new TreeMap<>();
        params.put("app_id", platformConfigProperties.getAlipayAppId());
        params.put("method", "alipay.trade.page.pay");
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("_input_charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        params.put("version", "1.0");
        params.put("return_url", platformConfigProperties.getAlipayReturnUrl());
        params.put("notify_url", platformConfigProperties.getAlipayNotifyUrl());

        BigDecimal totalAmount = orders.stream()
                .map(CustomerOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String outTradeNo;
        String subject;
        if (orders.size() == 1) {
            CustomerOrder order = orders.get(0);
            outTradeNo = order.getOrderNumber();
            subject = ("MK Order " + order.getOrderNumber()).replace("\"", "");
        } else {
            outTradeNo = generateBatchTradeNo(user.getId());
            subject = ("MK Orders x" + orders.size()).replace("\"", "");
            recordBatchPayment(user, outTradeNo, totalAmount, orders);
        }
        String bizContent = String.format(
                "{\"out_trade_no\":\"%s\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\",\"total_amount\":\"%s\",\"subject\":\"%s\"}",
                outTradeNo,
                totalAmount.toPlainString(),
                subject
        );
        params.put("biz_content", bizContent);

        try {
            String sign = AlipaySigner.sign(params, platformConfigProperties.getAlipayPrivateKey());
            params.put("sign", sign);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to sign request: " + e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("gateway", gateway);
        response.put("params", params);
        response.put("redirectUrl", buildRedirectUrl(gateway, params));
        return response;
    }

    @PostMapping("/recharge")
    public Map<String, Object> recharge(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User user = currentUser(request);

        BigDecimal amount = BigDecimal.ZERO;
        if (body.get("amount") instanceof Number number) {
            amount = BigDecimal.valueOf(number.doubleValue());
        } else if (body.get("amount") instanceof String text) {
            try {
                amount = new BigDecimal(text);
            } catch (Exception ignored) {
            }
        }
        if (amount.compareTo(BigDecimal.valueOf(0.01)) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than 0");
        }

        validateAlipayConfig(platformConfigProperties);

        String gateway = isBlank(platformConfigProperties.getAlipayGateway())
                ? "https://openapi-sandbox.dl.alipaydev.com/gateway.do"
                : platformConfigProperties.getAlipayGateway();

        Map<String, String> params = new TreeMap<>();
        params.put("app_id", platformConfigProperties.getAlipayAppId());
        params.put("method", "alipay.trade.page.pay");
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("_input_charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        params.put("version", "1.0");
        params.put("return_url", platformConfigProperties.getAlipayReturnUrl());
        params.put("notify_url", platformConfigProperties.getAlipayNotifyUrl());

        String outTradeNo = "RECHARGE_" + user.getId() + "_" + System.currentTimeMillis();
        String subject = ("Wallet Recharge " + outTradeNo).replace("\"", "");
        String bizContent = String.format(
                "{\"out_trade_no\":\"%s\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\",\"total_amount\":\"%s\",\"subject\":\"%s\"}",
                outTradeNo,
                amount.toPlainString(),
                subject
        );
        params.put("biz_content", bizContent);

        try {
            String sign = AlipaySigner.sign(params, platformConfigProperties.getAlipayPrivateKey());
            params.put("sign", sign);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to sign request: " + e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("gateway", gateway);
        response.put("params", params);
        response.put("redirectUrl", buildRedirectUrl(gateway, params));
        return response;
    }

    @RequestMapping(value = "/notify", method = {RequestMethod.POST, RequestMethod.GET})
    public String notifyPay(@RequestParam Map<String, String> params) {
        return alipayPaymentSyncService.handleCallback(params) ? "success" : "fail";
    }

    @RequestMapping(value = "/return", method = {RequestMethod.POST, RequestMethod.GET})
    public String returnPay(@RequestParam Map<String, String> params) {
        alipayPaymentSyncService.handleCallback(params);
        return "Payment processed";
    }

    @PostMapping("/confirm")
    public Map<String, Object> confirmPay(@RequestBody(required = false) Map<String, String> params) {
        Map<String, String> callbackParams = params == null ? Map.of() : params;
        Map<String, Object> response = new HashMap<>();
        response.put("success", alipayPaymentSyncService.handleCallback(callbackParams));
        response.put("outTradeNo", callbackParams.get("out_trade_no"));
        response.put("tradeStatus", callbackParams.get("trade_status"));
        return response;
    }

    private void validateAlipayConfig(PlatformConfigProperties config) {
        if (isBlank(config.getAlipayAppId()) || isBlank(config.getAlipayPrivateKey()) || isBlank(config.getAlipayPublicKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incomplete Alipay config");
        }
        if (isBlank(config.getAlipayNotifyUrl()) || isBlank(config.getAlipayReturnUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing notify_url or return_url");
        }
    }

    private List<CustomerOrder> resolvePayOrders(Map<String, Object> body, User user) {
        List<Long> orderIds = new ArrayList<>();
        if (body.get("orderId") instanceof Number number) {
            orderIds.add(number.longValue());
        }
        if (body.get("orderIds") instanceof Iterable<?> values) {
            for (Object value : values) {
                if (value instanceof Number number) {
                    orderIds.add(number.longValue());
                } else if (value instanceof String text && !text.isBlank()) {
                    orderIds.add(Long.parseLong(text));
                }
            }
        }
        List<Long> distinctOrderIds = orderIds.stream().distinct().toList();
        if (distinctOrderIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing orderId");
        }

        List<CustomerOrder> orders = new ArrayList<>();
        for (Long orderId : distinctOrderIds) {
            CustomerOrder order = customerOrderQueryMapper.selectById(orderId);
            if (order == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
            }
            if (order.getBuyer() == null || !order.getBuyer().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot pay for another user's order");
            }
            if (!"PENDING_PAYMENT".equalsIgnoreCase(order.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not pending payment");
            }
            orders.add(order);
        }
        return orders;
    }

    private void recordBatchPayment(User user, String batchTradeNo, BigDecimal amount, List<CustomerOrder> orders) {
        PaymentLog log = new PaymentLog();
        log.setUser(user);
        log.setOrderNumber(batchTradeNo);
        log.setAmount(amount);
        log.setType(LOG_TYPE_BATCH);
        log.setRemark(BATCH_REMARK_PREFIX + orders.stream()
                .map(CustomerOrder::getId)
                .map(String::valueOf)
                .collect(Collectors.joining(",")));
        paymentLogQueryMapper.insert(log);
    }

    private String generateBatchTradeNo(Long userId) {
        return BATCH_TRADE_PREFIX + userId + "_" + System.currentTimeMillis();
    }

    private String buildRedirectUrl(String gateway, Map<String, String> params) {
        if (isBlank(gateway)) {
            return "";
        }
        String query = params.entrySet().stream()
                .filter(entry -> !isBlank(entry.getValue()))
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
        if (query.isBlank()) {
            return gateway;
        }
        return gateway + (gateway.contains("?") ? "&" : "?") + query;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private User currentUser(HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return user;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
