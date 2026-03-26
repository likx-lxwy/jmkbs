package com.example.demo.controller;

import com.example.demo.mapper.CustomerOrderQueryMapper;
import com.example.demo.mapper.PaymentLogQueryMapper;
import com.example.demo.model.CustomerOrder;
import com.example.demo.model.PaymentLog;
import com.example.demo.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    private static final String LOG_TYPE_REVIEW = "REVIEW";

    private final PaymentLogQueryMapper paymentLogQueryMapper;
    private final CustomerOrderQueryMapper customerOrderQueryMapper;

    public PaymentController(PaymentLogQueryMapper paymentLogQueryMapper, CustomerOrderQueryMapper customerOrderQueryMapper) {
        this.paymentLogQueryMapper = paymentLogQueryMapper;
        this.customerOrderQueryMapper = customerOrderQueryMapper;
    }

    @GetMapping("/mine")
    public List<Map<String, Object>> myPayments(HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        List<Map<String, Object>> logs = paymentLogQueryMapper.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .filter(log -> !LOG_TYPE_REVIEW.equalsIgnoreCase(log.getType()))
                .map(this::toMap)
                .toList();
        if (!logs.isEmpty()) {
            return logs;
        }
        return customerOrderQueryMapper.selectByBuyerIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::fromOrder)
                .toList();
    }

    private Map<String, Object> toMap(PaymentLog log) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", log.getId());
        result.put("orderNumber", log.getOrderNumber());
        result.put("amount", log.getAmount());
        result.put("type", log.getType());
        result.put("remark", log.getRemark());
        result.put("createdAt", log.getCreatedAt());
        return result;
    }

    private Map<String, Object> fromOrder(CustomerOrder order) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", order.getId());
        result.put("orderNumber", order.getOrderNumber());
        result.put("amount", order.getTotalAmount());
        result.put("type", "PAY");
        result.put("remark", "订单支付（历史记录）");
        result.put("createdAt", order.getCreatedAt());
        return result;
    }
}
