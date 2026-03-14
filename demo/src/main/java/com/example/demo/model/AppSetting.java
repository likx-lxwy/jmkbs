package com.example.demo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "app_settings")
public class AppSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_approval_level", length = 10)
    private String orderApprovalLevel = "LOW"; // LOW or HIGH

    @Column(name = "ai_api_key", length = 255)
    private String aiApiKey;

    @Column(name = "map_api_key", length = 255)
    private String mapApiKey;

    @Column(name = "map_js_key", length = 255)
    private String mapJsKey;

    @Column(name = "map_js_sec", length = 255)
    private String mapJsSec;

    @Column(name = "alipay_app_id", length = 64)
    private String alipayAppId;

    @Column(name = "alipay_private_key", columnDefinition = "TEXT")
    private String alipayPrivateKey;

    @Column(name = "alipay_public_key", columnDefinition = "TEXT")
    private String alipayPublicKey;

    @Column(name = "alipay_gateway", length = 255)
    private String alipayGateway;

    @Column(name = "alipay_return_url", length = 255)
    private String alipayReturnUrl;

    @Column(name = "alipay_notify_url", length = 255)
    private String alipayNotifyUrl;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderApprovalLevel() {
        return orderApprovalLevel;
    }

    public void setOrderApprovalLevel(String orderApprovalLevel) {
        this.orderApprovalLevel = orderApprovalLevel;
    }

    public String getAiApiKey() {
        return aiApiKey;
    }

    public void setAiApiKey(String aiApiKey) {
        this.aiApiKey = aiApiKey;
    }

    public String getMapApiKey() {
        return mapApiKey;
    }

    public void setMapApiKey(String mapApiKey) {
        this.mapApiKey = mapApiKey;
    }

    public String getMapJsKey() {
        return mapJsKey;
    }

    public void setMapJsKey(String mapJsKey) {
        this.mapJsKey = mapJsKey;
    }

    public String getMapJsSec() {
        return mapJsSec;
    }

    public void setMapJsSec(String mapJsSec) {
        this.mapJsSec = mapJsSec;
    }

    public String getAlipayAppId() {
        return alipayAppId;
    }

    public void setAlipayAppId(String alipayAppId) {
        this.alipayAppId = alipayAppId;
    }

    public String getAlipayPrivateKey() {
        return alipayPrivateKey;
    }

    public void setAlipayPrivateKey(String alipayPrivateKey) {
        this.alipayPrivateKey = alipayPrivateKey;
    }

    public String getAlipayPublicKey() {
        return alipayPublicKey;
    }

    public void setAlipayPublicKey(String alipayPublicKey) {
        this.alipayPublicKey = alipayPublicKey;
    }

    public String getAlipayGateway() {
        return alipayGateway;
    }

    public void setAlipayGateway(String alipayGateway) {
        this.alipayGateway = alipayGateway;
    }

    public String getAlipayReturnUrl() {
        return alipayReturnUrl;
    }

    public void setAlipayReturnUrl(String alipayReturnUrl) {
        this.alipayReturnUrl = alipayReturnUrl;
    }

    public String getAlipayNotifyUrl() {
        return alipayNotifyUrl;
    }

    public void setAlipayNotifyUrl(String alipayNotifyUrl) {
        this.alipayNotifyUrl = alipayNotifyUrl;
    }
}
