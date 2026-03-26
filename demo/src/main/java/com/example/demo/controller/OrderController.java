package com.example.demo.controller;

import com.example.demo.dto.BatchOrderResponse;
import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.dto.OrderResponse;
import com.example.demo.dto.SubmitOrderReviewRequest;
import com.example.demo.model.CustomerOrder;
import com.example.demo.model.User;
import com.example.demo.service.AlipayPaymentSyncService;
import com.example.demo.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;
    private final AlipayPaymentSyncService alipayPaymentSyncService;

    public OrderController(OrderService orderService, AlipayPaymentSyncService alipayPaymentSyncService) {
        this.orderService = orderService;
        this.alipayPaymentSyncService = alipayPaymentSyncService;
    }

    @PostMapping
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request, HttpServletRequest httpRequest) {
        User user = (User) httpRequest.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        CustomerOrder order = orderService.createOrder(request, user);
        return orderService.toResponse(order);
    }

    @PostMapping("/batch")
    public BatchOrderResponse createBatch(@Valid @RequestBody CreateOrderRequest request, HttpServletRequest httpRequest) {
        User user = (User) httpRequest.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        List<OrderResponse> orders = orderService.createOrdersByMerchant(request, user)
                .stream()
                .map(orderService::toResponse)
                .toList();
        return new BatchOrderResponse(
                orders,
                orders.stream()
                        .map(OrderResponse::getTotalAmount)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                orders.size()
        );
    }

    @GetMapping("/mine")
    public List<OrderResponse> myOrders(HttpServletRequest httpRequest) {
        User user = (User) httpRequest.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        List<CustomerOrder> orders = orderService.listMyOrders(user);
        alipayPaymentSyncService.synchronizePendingOrders(orders);
        return orderService.listMyOrders(user).stream().map(orderService::toResponse).toList();
    }

    @PostMapping("/{id}/confirm-receipt")
    public void confirmReceipt(@PathVariable Long id, HttpServletRequest httpRequest) {
        User user = (User) httpRequest.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        orderService.confirmReceipt(id, user);
    }

    @PostMapping("/{id}/review")
    public void reviewMerchant(@PathVariable Long id,
                               @Valid @RequestBody SubmitOrderReviewRequest request,
                               HttpServletRequest httpRequest) {
        User user = (User) httpRequest.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        orderService.reviewMerchant(id, user, request.getRating(), request.getContent());
    }

    @PostMapping("/{id}/refund")
    public void refund(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest httpRequest) {
        User user = (User) httpRequest.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        String reason = body == null ? null : body.getOrDefault("reason", "");
        orderService.refundOrder(id, user, reason);
    }

    @PostMapping("/{id}/refund/approve")
    public void approveRefund(@PathVariable Long id, HttpServletRequest httpRequest) {
        User user = (User) httpRequest.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        orderService.approveRefund(id, user);
    }

    @PostMapping("/{id}/refund/reject")
    public void rejectRefund(@PathVariable Long id, HttpServletRequest httpRequest) {
        User user = (User) httpRequest.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        orderService.rejectRefund(id, user);
    }
}
