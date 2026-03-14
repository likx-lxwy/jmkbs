package com.example.demo.dto;

public class LoginResponse {
    private String token;
    private String username;
    private String role;
    private String merchantStatus;

    public LoginResponse() {
    }

    public LoginResponse(String token, String username, String role, String merchantStatus) {
        this.token = token;
        this.username = username;
        this.role = role;
        this.merchantStatus = merchantStatus;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
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
