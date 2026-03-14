package com.example.demo.controller;

import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.dto.OrderResponse;
import com.example.demo.model.CustomerOrder;
import com.example.demo.model.User;
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

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request, HttpServletRequest httpRequest) {
        User user = (User) httpRequest.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        CustomerOrder order = orderService.createOrder(request, user);
        return orderService.toResponse(order);
    }

    @GetMapping("/mine")
    public List<OrderResponse> myOrders(HttpServletRequest httpRequest) {
        User user = (User) httpRequest.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return orderService.listMyOrders(user).stream().map(orderService::toResponse).toList();
    }

    @PostMapping("/{id}/refund")
    public void refund(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest httpRequest) {
        User user = (User) httpRequest.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        String reason = body == null ? null : body.getOrDefault("reason", "");
        orderService.refundOrder(id, user, reason);
    }
}
