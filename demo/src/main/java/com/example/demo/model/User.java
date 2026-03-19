package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(nullable = false, length = 120)
    private String password;

    @Column(length = 120)
    private String email;

    @Column(nullable = false, length = 20)
    private String role; // USER, MERCHANT, ADMIN

    @Column(name = "merchant_status", length = 20)
    private String merchantStatus; // NONE, UNREVIEWED, PENDING, APPROVED, BANNED

    @Column(name = "wallet_balance", precision = 12, scale = 2, nullable = false)
    private java.math.BigDecimal walletBalance = java.math.BigDecimal.valueOf(50);

    @Column(name = "subscription_paid_until")
    private java.time.LocalDate subscriptionPaidUntil;

    public User() {
    }

    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.merchantStatus = "NONE";
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public String getMerchantStatus() {
        return merchantStatus;
    }

    public void setMerchantStatus(String merchantStatus) {
        this.merchantStatus = merchantStatus;
    }

    public java.math.BigDecimal getWalletBalance() {
        return walletBalance;
    }

    public void setWalletBalance(java.math.BigDecimal walletBalance) {
        this.walletBalance = walletBalance;
    }

    public java.time.LocalDate getSubscriptionPaidUntil() {
        return subscriptionPaidUntil;
    }

    public void setSubscriptionPaidUntil(java.time.LocalDate subscriptionPaidUntil) {
        this.subscriptionPaidUntil = subscriptionPaidUntil;
    }
}
