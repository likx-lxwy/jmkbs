package com.example.demo.dto;

import java.math.BigDecimal;

public class AdminOverviewResponse {
    private long totalUsers;
    private long totalMerchants;
    private long pendingMerchants;
    private long approvedMerchants;
    private long totalOrders;
    private BigDecimal totalRevenue;
    private long productCount;
    private long lowStockCount;

    public AdminOverviewResponse() {
    }

    public AdminOverviewResponse(long totalUsers, long totalMerchants, long pendingMerchants, long approvedMerchants,
                                 long totalOrders, BigDecimal totalRevenue, long productCount, long lowStockCount) {
        this.totalUsers = totalUsers;
        this.totalMerchants = totalMerchants;
        this.pendingMerchants = pendingMerchants;
        this.approvedMerchants = approvedMerchants;
        this.totalOrders = totalOrders;
        this.totalRevenue = totalRevenue;
        this.productCount = productCount;
        this.lowStockCount = lowStockCount;
    }

    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getTotalMerchants() {
        return totalMerchants;
    }

    public void setTotalMerchants(long totalMerchants) {
        this.totalMerchants = totalMerchants;
    }

    public long getPendingMerchants() {
        return pendingMerchants;
    }

    public void setPendingMerchants(long pendingMerchants) {
        this.pendingMerchants = pendingMerchants;
    }

    public long getApprovedMerchants() {
        return approvedMerchants;
    }

    public void setApprovedMerchants(long approvedMerchants) {
        this.approvedMerchants = approvedMerchants;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public long getProductCount() {
        return productCount;
    }

    public void setProductCount(long productCount) {
        this.productCount = productCount;
    }

    public long getLowStockCount() {
        return lowStockCount;
    }

    public void setLowStockCount(long lowStockCount) {
        this.lowStockCount = lowStockCount;
    }
}
