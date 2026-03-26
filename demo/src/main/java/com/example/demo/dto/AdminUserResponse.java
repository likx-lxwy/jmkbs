package com.example.demo.dto;

import java.math.BigDecimal;

public class AdminUserResponse {

    private Long id;
    private String username;
    private String email;
    private String role;
    private String accountStatus;
    private String merchantStatus;
    private String merchantStoreName;
    private BigDecimal walletBalance;

    public AdminUserResponse() {
    }

    public AdminUserResponse(Long id,
                             String username,
                             String email,
                             String role,
                             String accountStatus,
                             String merchantStatus,
                             String merchantStoreName,
                             BigDecimal walletBalance) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.role = role;
        this.accountStatus = accountStatus;
        this.merchantStatus = merchantStatus;
        this.merchantStoreName = merchantStoreName;
        this.walletBalance = walletBalance;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getMerchantStatus() {
        return merchantStatus;
    }

    public void setMerchantStatus(String merchantStatus) {
        this.merchantStatus = merchantStatus;
    }

    public String getMerchantStoreName() {
        return merchantStoreName;
    }

    public void setMerchantStoreName(String merchantStoreName) {
        this.merchantStoreName = merchantStoreName;
    }

    public BigDecimal getWalletBalance() {
        return walletBalance;
    }

    public void setWalletBalance(BigDecimal walletBalance) {
        this.walletBalance = walletBalance;
    }
}
