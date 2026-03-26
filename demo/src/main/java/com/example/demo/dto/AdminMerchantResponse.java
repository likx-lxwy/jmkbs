package com.example.demo.dto;

public class AdminMerchantResponse {
    private Long id;
    private String username;
    private String email;
    private String role;
    private String merchantStatus;
    private String storeName;
    private String contactName;
    private String contactPhone;
    private String businessAddress;
    private String licenseNumber;
    private String description;

    public AdminMerchantResponse() {
    }

    public AdminMerchantResponse(Long id,
                                 String username,
                                 String email,
                                 String role,
                                 String merchantStatus,
                                 String storeName,
                                 String contactName,
                                 String contactPhone,
                                 String businessAddress,
                                 String licenseNumber,
                                 String description) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.role = role;
        this.merchantStatus = merchantStatus;
        this.storeName = storeName;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.businessAddress = businessAddress;
        this.licenseNumber = licenseNumber;
        this.description = description;
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

    public String getMerchantStatus() {
        return merchantStatus;
    }

    public void setMerchantStatus(String merchantStatus) {
        this.merchantStatus = merchantStatus;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getBusinessAddress() {
        return businessAddress;
    }

    public void setBusinessAddress(String businessAddress) {
        this.businessAddress = businessAddress;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
