package com.example.demo.dto;

public class AdminMerchantResponse {
    private Long id;
    private String username;
    private String role;
    private String merchantStatus;

    public AdminMerchantResponse() {
    }

    public AdminMerchantResponse(Long id, String username, String role, String merchantStatus) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.merchantStatus = merchantStatus;
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
}
