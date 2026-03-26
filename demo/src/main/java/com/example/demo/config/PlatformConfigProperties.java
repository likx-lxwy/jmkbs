package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.platform")
public class PlatformConfigProperties {

    private String mapApiKey = "";
    private String mapJsKey = "";
    private String mapJsSec = "";
    private String alipayAppId = "";
    private String alipayPrivateKey = "";
    private String alipayPublicKey = "";
    private String alipayGateway = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private String alipayReturnUrl = "";
    private String alipayNotifyUrl = "";

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
