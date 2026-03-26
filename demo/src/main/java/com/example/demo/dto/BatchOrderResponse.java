package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BatchOrderResponse {

    private List<OrderResponse> orders = new ArrayList<>();
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private Integer orderCount = 0;

    public BatchOrderResponse() {
    }

    public BatchOrderResponse(List<OrderResponse> orders, BigDecimal totalAmount, Integer orderCount) {
        this.orders = orders;
        this.totalAmount = totalAmount;
        this.orderCount = orderCount;
    }

    public List<OrderResponse> getOrders() {
        return orders;
    }

    public void setOrders(List<OrderResponse> orders) {
        this.orders = orders;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }
}
