package com.example.demo.dto;

public class CaptchaResponse {

    private String captchaToken;
    private String imageData;
    private long expiresInSeconds;

    public CaptchaResponse() {
    }

    public CaptchaResponse(String captchaToken, String imageData, long expiresInSeconds) {
        this.captchaToken = captchaToken;
        this.imageData = imageData;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getCaptchaToken() {
        return captchaToken;
    }

    public void setCaptchaToken(String captchaToken) {
        this.captchaToken = captchaToken;
    }

    public String getImageData() {
        return imageData;
    }

    public void setImageData(String imageData) {
        this.imageData = imageData;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
