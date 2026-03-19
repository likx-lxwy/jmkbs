package com.example.demo.controller;

import com.example.demo.model.AppSetting;
import com.example.demo.model.CustomerOrder;
import com.example.demo.model.PaymentLog;
import com.example.demo.model.User;
import com.example.demo.repository.AppSettingRepository;
import com.example.demo.repository.CustomerOrderRepository;
import com.example.demo.repository.PaymentLogRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.OrderService;
import com.example.demo.util.AlipaySigner;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/payments/alipay")
@CrossOrigin(origins = "*")
public class AlipayController {

    private final AppSettingRepository appSettingRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderService orderService;
    private final UserRepository userRepository;
    private final PaymentLogRepository paymentLogRepository;

    public AlipayController(AppSettingRepository appSettingRepository,
                            CustomerOrderRepository customerOrderRepository,
                            OrderService orderService,
                            UserRepository userRepository,
                            PaymentLogRepository paymentLogRepository) {
        this.appSettingRepository = appSettingRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.orderService = orderService;
        this.userRepository = userRepository;
        this.paymentLogRepository = paymentLogRepository;
    }

    @PostMapping("/pay")
    public Map<String, Object> pay(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }

        Long orderId = body.get("orderId") instanceof Number ? ((Number) body.get("orderId")).longValue() : null;
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少订单ID");
        }

        CustomerOrder order = customerOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (order.getBuyer() == null || !order.getBuyer().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能支付自己的订单");
        }
        if (!"PENDING_PAYMENT".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单状态不是待支付");
        }

        AppSetting setting = appSettingRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先配置支付宝参数"));
        validateAlipayConfig(setting);

        String gateway = isBlank(setting.getAlipayGateway())
                ? "https://openapi-sandbox.dl.alipaydev.com/gateway.do"
                : setting.getAlipayGateway();

        Map<String, String> params = new TreeMap<>();
        params.put("app_id", setting.getAlipayAppId());
        params.put("method", "alipay.trade.page.pay");
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("_input_charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        params.put("version", "1.0");
        params.put("return_url", setting.getAlipayReturnUrl());
        params.put("notify_url", setting.getAlipayNotifyUrl());

        String subject = ("MK订单" + order.getOrderNumber()).replace("\"", "");
        String bizContent = String.format(
                "{\"out_trade_no\":\"%s\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\",\"total_amount\":\"%s\",\"subject\":\"%s\"}",
                order.getOrderNumber(),
                (order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount()).toPlainString(),
                subject
        );
        params.put("biz_content", bizContent);

        try {
            String sign = AlipaySigner.sign(params, setting.getAlipayPrivateKey());
            params.put("sign", sign);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "签名失败: " + e.getMessage());
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("gateway", gateway);
        resp.put("params", params);
        return resp;
    }

    @PostMapping("/recharge")
    public Map<String, Object> recharge(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }

        BigDecimal amount = BigDecimal.ZERO;
        if (body.get("amount") instanceof Number n) {
            amount = BigDecimal.valueOf(n.doubleValue());
        } else if (body.get("amount") instanceof String s) {
            try {
                amount = new BigDecimal(s);
            } catch (Exception ignored) {
            }
        }
        if (amount.compareTo(BigDecimal.valueOf(0.01)) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "金额必须大于0");
        }

        AppSetting setting = appSettingRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先配置支付宝参数"));
        validateAlipayConfig(setting);

        String gateway = isBlank(setting.getAlipayGateway())
                ? "https://openapi-sandbox.dl.alipaydev.com/gateway.do"
                : setting.getAlipayGateway();

        Map<String, String> params = new TreeMap<>();
        params.put("app_id", setting.getAlipayAppId());
        params.put("method", "alipay.trade.page.pay");
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("_input_charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        params.put("version", "1.0");
        params.put("return_url", setting.getAlipayReturnUrl());
        params.put("notify_url", setting.getAlipayNotifyUrl());

        String outTradeNo = "RECHARGE_" + user.getId() + "_" + System.currentTimeMillis();
        String subject = ("金币充值" + outTradeNo).replace("\"", "");
        String bizContent = String.format(
                "{\"out_trade_no\":\"%s\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\",\"total_amount\":\"%s\",\"subject\":\"%s\"}",
                outTradeNo,
                amount.toPlainString(),
                subject
        );
        params.put("biz_content", bizContent);

        try {
            String sign = AlipaySigner.sign(params, setting.getAlipayPrivateKey());
            params.put("sign", sign);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "签名失败: " + e.getMessage());
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("gateway", gateway);
        resp.put("params", params);
        return resp;
    }

    @RequestMapping(value = "/notify", method = {RequestMethod.POST, RequestMethod.GET})
    public String notifyPay(@RequestParam Map<String, String> params) {
        return handleCallback(params) ? "success" : "fail";
    }

    @RequestMapping(value = "/return", method = {RequestMethod.POST, RequestMethod.GET})
    public String returnPay(@RequestParam Map<String, String> params) {
        handleCallback(params);
        return "支付处理完成，请返回订单页查看状态。";
    }

    private boolean handleCallback(Map<String, String> params) {
        AppSetting setting = appSettingRepository.findAll().stream().findFirst().orElse(null);
        if (setting == null || isBlank(setting.getAlipayPublicKey()) || isBlank(setting.getAlipayAppId())) {
            return false;
        }

        String outTradeNo = params.get("out_trade_no");
        String sign = params.get("sign");
        String tradeStatus = params.get("trade_status");
        String appId = params.get("app_id");

        if (isBlank(sign) || isBlank(outTradeNo)) {
            return false;
        }

        boolean verified = AlipaySigner.verify(params, sign, setting.getAlipayPublicKey());
        if (!verified) {
            return false;
        }
        if (!setting.getAlipayAppId().equals(appId)) {
            return false;
        }

        if (!"TRADE_SUCCESS".equalsIgnoreCase(tradeStatus) && !"TRADE_FINISHED".equalsIgnoreCase(tradeStatus)) {
            return true;
        }

        try {
            if (outTradeNo.startsWith("RECHARGE_")) {
                handleRecharge(outTradeNo, params);
            } else {
                validateOrderAmount(outTradeNo, params);
                orderService.markOrderPaid(outTradeNo);
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private void validateOrderAmount(String outTradeNo, Map<String, String> params) {
        CustomerOrder order = customerOrderRepository.findByOrderNumber(outTradeNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        BigDecimal orderAmount = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        BigDecimal callbackAmount = new BigDecimal(params.getOrDefault("total_amount",
                params.getOrDefault("buyer_pay_amount", params.getOrDefault("receipt_amount", "0"))));
        if (orderAmount.compareTo(callbackAmount) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付金额与订单金额不匹配");
        }
    }

    private void handleRecharge(String outTradeNo, Map<String, String> params) {
        try {
            String[] parts = outTradeNo.split("_");
            if (parts.length < 3) {
                return;
            }

            Long userId = Long.parseLong(parts[1]);
            BigDecimal amount = new BigDecimal(params.getOrDefault("total_amount",
                    params.getOrDefault("buyer_pay_amount", params.getOrDefault("receipt_amount", "0"))));
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return;
            }
            if (paymentLogRepository.existsByOrderNumberAndType(outTradeNo, "RECHARGE")) {
                return;
            }

            BigDecimal current = user.getWalletBalance() == null ? BigDecimal.ZERO : user.getWalletBalance();
            user.setWalletBalance(current.add(amount));
            userRepository.save(user);

            PaymentLog log = new PaymentLog();
            log.setUser(user);
            log.setAmount(amount);
            log.setType("RECHARGE");
            log.setRemark("支付宝充值");
            log.setOrderNumber(outTradeNo);
            paymentLogRepository.save(log);
        } catch (Exception ignored) {
        }
    }

    private void validateAlipayConfig(AppSetting setting) {
        if (isBlank(setting.getAlipayAppId()) || isBlank(setting.getAlipayPrivateKey()) || isBlank(setting.getAlipayPublicKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先完整配置支付宝 appId、应用私钥和支付宝公钥");
        }
        if (isBlank(setting.getAlipayNotifyUrl()) || isBlank(setting.getAlipayReturnUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请配置 return_url 和 notify_url");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

