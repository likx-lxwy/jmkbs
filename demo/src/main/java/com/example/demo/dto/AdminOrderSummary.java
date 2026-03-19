package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AdminOrderSummary {
    private Long id;
    private String orderNumber;
    private String customerName;
    private String status;
    private String payMethod;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private int itemCount;

    public AdminOrderSummary() {
    }

    public AdminOrderSummary(Long id, String orderNumber, String customerName, String status, String payMethod, BigDecimal totalAmount, LocalDateTime createdAt, int itemCount) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.status = status;
        this.payMethod = payMethod;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.itemCount = itemCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayMethod() {
        return payMethod;
    }

    public void setPayMethod(String payMethod) {
        this.payMethod = payMethod;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }
}
