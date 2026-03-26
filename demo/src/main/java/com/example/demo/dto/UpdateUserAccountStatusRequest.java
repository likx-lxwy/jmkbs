package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateUserAccountStatusRequest {

    @NotBlank
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
