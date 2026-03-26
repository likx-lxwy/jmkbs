package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.List;

public class OrderResponse {

    private Long orderId;
    private String orderNumber;
    private String status;
    private BigDecimal totalAmount;
    private List<OrderLine> items;
    private java.time.LocalDateTime createdAt;
    private String payMethod;
    private String refundReason;
    private String buyerName;
    private String merchantName;
    private Long refundChatCount;
    private Integer merchantRating;
    private String merchantReview;
    private java.time.LocalDateTime merchantReviewedAt;
    private boolean merchantReviewed;

    public OrderResponse() {
    }

    public OrderResponse(Long orderId, String orderNumber, String status, BigDecimal totalAmount, List<OrderLine> items, java.time.LocalDateTime createdAt, String payMethod, String refundReason) {
        this(orderId, orderNumber, status, totalAmount, items, createdAt, payMethod, refundReason, null, null, 0L, null, null, null, false);
    }

    public OrderResponse(Long orderId, String orderNumber, String status, BigDecimal totalAmount, List<OrderLine> items, java.time.LocalDateTime createdAt, String payMethod, String refundReason, String buyerName, String merchantName, Long refundChatCount) {
        this(orderId, orderNumber, status, totalAmount, items, createdAt, payMethod, refundReason, buyerName, merchantName, refundChatCount, null, null, null, false);
    }

    public OrderResponse(Long orderId, String orderNumber, String status, BigDecimal totalAmount, List<OrderLine> items, java.time.LocalDateTime createdAt, String payMethod, String refundReason, String buyerName, String merchantName, Long refundChatCount, Integer merchantRating, String merchantReview, java.time.LocalDateTime merchantReviewedAt, boolean merchantReviewed) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.status = status;
        this.totalAmount = totalAmount;
        this.items = items;
        this.createdAt = createdAt;
        this.payMethod = payMethod;
        this.refundReason = refundReason;
        this.buyerName = buyerName;
        this.merchantName = merchantName;
        this.refundChatCount = refundChatCount;
        this.merchantRating = merchantRating;
        this.merchantReview = merchantReview;
        this.merchantReviewedAt = merchantReviewedAt;
        this.merchantReviewed = merchantReviewed;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public List<OrderLine> getItems() {
        return items;
    }

    public void setItems(List<OrderLine> items) {
        this.items = items;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPayMethod() {
        return payMethod;
    }

    public void setPayMethod(String payMethod) {
        this.payMethod = payMethod;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public Long getRefundChatCount() {
        return refundChatCount;
    }

    public void setRefundChatCount(Long refundChatCount) {
        this.refundChatCount = refundChatCount;
    }

    public Integer getMerchantRating() {
        return merchantRating;
    }

    public void setMerchantRating(Integer merchantRating) {
        this.merchantRating = merchantRating;
    }

    public String getMerchantReview() {
        return merchantReview;
    }

    public void setMerchantReview(String merchantReview) {
        this.merchantReview = merchantReview;
    }

    public java.time.LocalDateTime getMerchantReviewedAt() {
        return merchantReviewedAt;
    }

    public void setMerchantReviewedAt(java.time.LocalDateTime merchantReviewedAt) {
        this.merchantReviewedAt = merchantReviewedAt;
    }

    public boolean isMerchantReviewed() {
        return merchantReviewed;
    }

    public void setMerchantReviewed(boolean merchantReviewed) {
        this.merchantReviewed = merchantReviewed;
    }

    public static class OrderLine {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private String sizeLabel;

        public OrderLine() {
        }

        public OrderLine(Long productId, String productName, Integer quantity, BigDecimal unitPrice, String sizeLabel) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.sizeLabel = sizeLabel;
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public String getSizeLabel() {
            return sizeLabel;
        }

        public void setSizeLabel(String sizeLabel) {
            this.sizeLabel = sizeLabel;
        }
    }
}
