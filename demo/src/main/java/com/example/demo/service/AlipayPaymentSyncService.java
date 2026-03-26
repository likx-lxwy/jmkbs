package com.example.demo.service;

import com.example.demo.config.PlatformConfigProperties;
import com.example.demo.mapper.CustomerOrderQueryMapper;
import com.example.demo.mapper.PaymentLogQueryMapper;
import com.example.demo.mapper.UserQueryMapper;
import com.example.demo.model.CustomerOrder;
import com.example.demo.model.PaymentLog;
import com.example.demo.model.User;
import com.example.demo.util.AlipaySigner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AlipayPaymentSyncService {

    private static final String PAY_ALIPAY = "ALIPAY";
    private static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String TRADE_SUCCESS = "TRADE_SUCCESS";
    private static final String TRADE_FINISHED = "TRADE_FINISHED";
    private static final String BATCH_TRADE_PREFIX = "PAYB_";
    private static final String LOG_TYPE_BATCH = "BATCH";
    private static final String BATCH_REMARK_PREFIX = "ORDER_IDS:";
    private static final String DEFAULT_GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    private final OrderService orderService;
    private final PlatformConfigProperties platformConfigProperties;
    private final CustomerOrderQueryMapper customerOrderQueryMapper;
    private final UserQueryMapper userQueryMapper;
    private final PaymentLogQueryMapper paymentLogQueryMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public AlipayPaymentSyncService(OrderService orderService,
                                    PlatformConfigProperties platformConfigProperties,
                                    CustomerOrderQueryMapper customerOrderQueryMapper,
                                    UserQueryMapper userQueryMapper,
                                    PaymentLogQueryMapper paymentLogQueryMapper) {
        this.orderService = orderService;
        this.platformConfigProperties = platformConfigProperties;
        this.customerOrderQueryMapper = customerOrderQueryMapper;
        this.userQueryMapper = userQueryMapper;
        this.paymentLogQueryMapper = paymentLogQueryMapper;
    }

    @Transactional
    public boolean handleCallback(Map<String, String> params) {
        Map<String, String> callbackParams = params == null ? new HashMap<>() : new HashMap<>(params);
        String outTradeNo = trimToNull(callbackParams.get("out_trade_no"));
        if (isBlank(outTradeNo)) {
            return false;
        }

        boolean verifiedCallback = isVerifiedCallback(callbackParams);
        if (!isPaidTradeStatus(callbackParams.get("trade_status"))) {
            TradeQueryResult queriedTrade = queryTrade(outTradeNo, callbackParams.get("trade_no"));
            if (queriedTrade != null) {
                callbackParams.putAll(queriedTrade.toCallbackParams());
            }
        }

        if (!isPaidTradeStatus(callbackParams.get("trade_status"))) {
            return verifiedCallback;
        }

        try {
            processSuccessfulTrade(outTradeNo, callbackParams);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Transactional
    public void synchronizePendingOrders(List<CustomerOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }

        Set<String> processedTradeNumbers = new LinkedHashSet<>();
        Map<String, TradeQueryResult> queryCache = new LinkedHashMap<>();

        for (CustomerOrder order : orders) {
            if (!isPendingAlipayOrder(order)) {
                continue;
            }

            synchronizeTradeIfPaid(order.getOrderNumber(), null, order.getTotalAmount(), processedTradeNumbers, queryCache);
            if (order.getId() == null) {
                continue;
            }

            List<PaymentLog> batchLogs = paymentLogQueryMapper.findBatchLogsByOrderId(
                    order.getId(),
                    LOG_TYPE_BATCH,
                    BATCH_REMARK_PREFIX
            );
            for (PaymentLog batchLog : batchLogs) {
                synchronizeTradeIfPaid(
                        batchLog.getOrderNumber(),
                        null,
                        batchLog.getAmount(),
                        processedTradeNumbers,
                        queryCache
                );
            }
        }
    }

    private boolean isVerifiedCallback(Map<String, String> params) {
        if (params == null || isBlank(platformConfigProperties.getAlipayPublicKey()) || isBlank(platformConfigProperties.getAlipayAppId())) {
            return false;
        }

        String sign = trimToNull(params.get("sign"));
        String appId = firstNonBlank(params.get("app_id"), params.get("auth_app_id"));
        if (isBlank(sign) || isBlank(appId)) {
            return false;
        }
        if (!platformConfigProperties.getAlipayAppId().equals(appId)) {
            return false;
        }
        return AlipaySigner.verify(params, sign, platformConfigProperties.getAlipayPublicKey());
    }

    private void synchronizeTradeIfPaid(String outTradeNo,
                                        String tradeNo,
                                        BigDecimal expectedAmount,
                                        Set<String> processedTradeNumbers,
                                        Map<String, TradeQueryResult> queryCache) {
        String tradeKey = trimToNull(outTradeNo);
        if (tradeKey == null || !processedTradeNumbers.add(tradeKey)) {
            return;
        }

        TradeQueryResult queriedTrade = queryCache.computeIfAbsent(tradeKey, ignored -> queryTrade(outTradeNo, tradeNo));
        if (queriedTrade == null || !isPaidTradeStatus(queriedTrade.tradeStatus)) {
            return;
        }
        if (!amountMatches(expectedAmount, queriedTrade.resolvePaidAmount())) {
            return;
        }

        try {
            processSuccessfulTrade(tradeKey, queriedTrade.toCallbackParams());
        } catch (RuntimeException ignored) {
        }
    }

    private void processSuccessfulTrade(String outTradeNo, Map<String, String> params) {
        if (outTradeNo.startsWith("RECHARGE_")) {
            handleRecharge(outTradeNo, params);
            return;
        }
        if (outTradeNo.startsWith(BATCH_TRADE_PREFIX)) {
            handleBatchOrderPayment(outTradeNo, params);
            return;
        }
        validateOrderAmount(outTradeNo, params);
        orderService.markOrderPaid(outTradeNo);
    }

    private void validateOrderAmount(String outTradeNo, Map<String, String> params) {
        CustomerOrder order = customerOrderQueryMapper.selectByOrderNumber(outTradeNo);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        BigDecimal orderAmount = defaultAmount(order.getTotalAmount());
        BigDecimal callbackAmount = resolveCallbackAmount(params);
        if (!amountMatches(orderAmount, callbackAmount)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Callback amount does not match order amount");
        }
    }

    private void handleBatchOrderPayment(String outTradeNo, Map<String, String> params) {
        PaymentLog batchLog = paymentLogQueryMapper.findByOrderNumberOrderByIdAsc(outTradeNo)
                .stream()
                .filter(log -> LOG_TYPE_BATCH.equalsIgnoreCase(log.getType()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch payment not found"));

        BigDecimal callbackAmount = resolveCallbackAmount(params);
        BigDecimal expectedAmount = defaultAmount(batchLog.getAmount());
        if (!amountMatches(expectedAmount, callbackAmount)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Callback amount does not match batch amount");
        }

        for (Long orderId : parseBatchOrderIds(batchLog.getRemark())) {
            CustomerOrder order = customerOrderQueryMapper.selectById(orderId);
            if (order == null) {
                continue;
            }
            orderService.markOrderPaid(order.getOrderNumber());
        }
    }

    private void handleRecharge(String outTradeNo, Map<String, String> params) {
        String[] parts = outTradeNo.split("_");
        if (parts.length < 3) {
            return;
        }

        Long userId = Long.parseLong(parts[1]);
        BigDecimal amount = resolveCallbackAmount(params);
        User user = userQueryMapper.selectById(userId);
        if (user == null || paymentLogQueryMapper.existsByOrderNumberAndType(outTradeNo, "RECHARGE")) {
            return;
        }

        BigDecimal current = user.getWalletBalance() == null ? BigDecimal.ZERO : user.getWalletBalance();
        user.setWalletBalance(current.add(amount));
        userQueryMapper.update(user);

        PaymentLog log = new PaymentLog();
        log.setUser(user);
        log.setAmount(amount);
        log.setType("RECHARGE");
        log.setRemark("alipay recharge");
        log.setOrderNumber(outTradeNo);
        paymentLogQueryMapper.insert(log);
    }

    private TradeQueryResult queryTrade(String outTradeNo, String tradeNo) {
        if (isBlank(outTradeNo)
                || isBlank(platformConfigProperties.getAlipayAppId())
                || isBlank(platformConfigProperties.getAlipayPrivateKey())) {
            return null;
        }

        try {
            Map<String, String> params = new TreeMap<>();
            params.put("app_id", platformConfigProperties.getAlipayAppId());
            params.put("method", "alipay.trade.query");
            params.put("format", "JSON");
            params.put("charset", "utf-8");
            params.put("sign_type", "RSA2");
            params.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            params.put("version", "1.0");
            params.put("biz_content", buildTradeQueryBizContent(outTradeNo, tradeNo));
            params.put("sign", AlipaySigner.sign(params, platformConfigProperties.getAlipayPrivateKey()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveGateway()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(buildFormBody(params)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300 || isBlank(response.body())) {
                return null;
            }
            return parseTradeQueryResponse(response.body());
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildTradeQueryBizContent(String outTradeNo, String tradeNo) throws Exception {
        StringBuilder bizContent = new StringBuilder();
        bizContent.append("{\"out_trade_no\":\"").append(escapeJson(outTradeNo)).append("\"");
        String normalizedTradeNo = trimToNull(tradeNo);
        if (normalizedTradeNo != null) {
            bizContent.append(",\"trade_no\":\"").append(escapeJson(normalizedTradeNo)).append("\"");
        }
        bizContent.append("}");
        return bizContent.toString();
    }

    private TradeQueryResult parseTradeQueryResponse(String body) {
        String responseJson = extractNamedJsonObject(body, "alipay_trade_query_response");
        if (isBlank(responseJson)) {
            return null;
        }
        if (!"10000".equals(extractJsonString(responseJson, "code"))) {
            return null;
        }

        TradeQueryResult result = new TradeQueryResult();
        result.outTradeNo = trimToNull(extractJsonString(responseJson, "out_trade_no"));
        result.tradeNo = trimToNull(extractJsonString(responseJson, "trade_no"));
        result.tradeStatus = trimToNull(extractJsonString(responseJson, "trade_status"));
        result.totalAmount = parseBigDecimal(extractJsonString(responseJson, "total_amount"));
        result.buyerPayAmount = parseBigDecimal(extractJsonString(responseJson, "buyer_pay_amount"));
        result.receiptAmount = parseBigDecimal(extractJsonString(responseJson, "receipt_amount"));
        return result;
    }

    private String buildFormBody(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> !isBlank(entry.getValue()))
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private List<Long> parseBatchOrderIds(String remark) {
        if (remark == null || !remark.startsWith(BATCH_REMARK_PREFIX)) {
            return List.of();
        }

        List<Long> orderIds = new ArrayList<>();
        for (String value : remark.substring(BATCH_REMARK_PREFIX.length()).split(",")) {
            String trimmed = value == null ? "" : value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            orderIds.add(Long.parseLong(trimmed));
        }
        return orderIds;
    }

    private BigDecimal resolveCallbackAmount(Map<String, String> params) {
        BigDecimal totalAmount = parseBigDecimal(params == null ? null : params.get("total_amount"));
        if (totalAmount != null) {
            return totalAmount;
        }
        BigDecimal buyerPayAmount = parseBigDecimal(params == null ? null : params.get("buyer_pay_amount"));
        if (buyerPayAmount != null) {
            return buyerPayAmount;
        }
        BigDecimal receiptAmount = parseBigDecimal(params == null ? null : params.get("receipt_amount"));
        return receiptAmount == null ? BigDecimal.ZERO : receiptAmount;
    }

    private boolean isPendingAlipayOrder(CustomerOrder order) {
        if (order == null) {
            return false;
        }
        if (!STATUS_PENDING_PAYMENT.equalsIgnoreCase(order.getStatus())) {
            return false;
        }
        return PAY_ALIPAY.equalsIgnoreCase(order.getPayMethod());
    }

    private boolean isPaidTradeStatus(String tradeStatus) {
        String normalized = normalize(tradeStatus);
        return TRADE_SUCCESS.equals(normalized) || TRADE_FINISHED.equals(normalized);
    }

    private boolean amountMatches(BigDecimal expectedAmount, BigDecimal actualAmount) {
        if (expectedAmount == null || actualAmount == null) {
            return false;
        }
        return expectedAmount.compareTo(actualAmount) == 0;
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private BigDecimal parseBigDecimal(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveGateway() {
        return isBlank(platformConfigProperties.getAlipayGateway())
                ? DEFAULT_GATEWAY
                : platformConfigProperties.getAlipayGateway();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first.trim() : trimToNull(second);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return trimToNull(value) == null;
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String extractNamedJsonObject(String json, String key) {
        if (isBlank(json) || isBlank(key)) {
            return null;
        }
        String marker = "\"" + key + "\":";
        int markerIndex = json.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        int objectStart = json.indexOf('{', markerIndex + marker.length());
        if (objectStart < 0) {
            return null;
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = objectStart; i < json.length(); i++) {
            char current = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
                continue;
            }
            if (current == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(objectStart, i + 1);
                }
            }
        }
        return null;
    }

    private String extractJsonString(String json, String key) {
        if (isBlank(json) || isBlank(key)) {
            return null;
        }
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static class TradeQueryResult {
        private String outTradeNo;
        private String tradeNo;
        private String tradeStatus;
        private BigDecimal totalAmount;
        private BigDecimal buyerPayAmount;
        private BigDecimal receiptAmount;

        private BigDecimal resolvePaidAmount() {
            if (totalAmount != null) {
                return totalAmount;
            }
            if (buyerPayAmount != null) {
                return buyerPayAmount;
            }
            return receiptAmount;
        }

        private Map<String, String> toCallbackParams() {
            Map<String, String> params = new HashMap<>();
            putIfPresent(params, "out_trade_no", outTradeNo);
            putIfPresent(params, "trade_no", tradeNo);
            putIfPresent(params, "trade_status", tradeStatus);
            putIfPresent(params, "total_amount", totalAmount);
            putIfPresent(params, "buyer_pay_amount", buyerPayAmount);
            putIfPresent(params, "receipt_amount", receiptAmount);
            return params;
        }

        private void putIfPresent(Map<String, String> params, String key, Object value) {
            if (value != null) {
                params.put(key, String.valueOf(value));
            }
        }
    }
}
