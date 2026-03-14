package com.example.demo.controller;

import com.example.demo.model.CustomerOrder;
import com.example.demo.model.PaymentLog;
import com.example.demo.model.User;
import com.example.demo.repository.CustomerOrderRepository;
import com.example.demo.repository.PaymentLogRepository;
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

    private final PaymentLogRepository paymentLogRepository;
    private final CustomerOrderRepository customerOrderRepository;

    public PaymentController(PaymentLogRepository paymentLogRepository, CustomerOrderRepository customerOrderRepository) {
        this.paymentLogRepository = paymentLogRepository;
        this.customerOrderRepository = customerOrderRepository;
    }

    @GetMapping("/mine")
    public List<Map<String, Object>> myPayments(HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        List<Map<String, Object>> logs = paymentLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toMap)
                .toList();
        if (!logs.isEmpty()) {
            return logs;
        }
        // 兜底：老数据没有流水时，用订单记录生成只读视图
        return customerOrderRepository.findByBuyerIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::fromOrder)
                .toList();
    }

    private Map<String, Object> toMap(PaymentLog log) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", log.getId());
        m.put("orderNumber", log.getOrderNumber());
        m.put("amount", log.getAmount());
        m.put("type", log.getType());
        m.put("remark", log.getRemark());
        m.put("createdAt", log.getCreatedAt());
        return m;
    }

    private Map<String, Object> fromOrder(CustomerOrder order) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", order.getId());
        m.put("orderNumber", order.getOrderNumber());
        m.put("amount", order.getTotalAmount());
        m.put("type", "PAY");
        m.put("remark", "订单支付（历史记录）");
        m.put("createdAt", order.getCreatedAt());
        return m;
    }
}
