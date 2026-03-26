package com.example.demo.controller;

import com.example.demo.dto.MerchantProfileRequest;
import com.example.demo.dto.MerchantProfileResponse;
import com.example.demo.mapper.UserQueryMapper;
import com.example.demo.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/merchant/profile")
@CrossOrigin(origins = "*")
public class MerchantProfileController {

    private final UserQueryMapper userQueryMapper;

    public MerchantProfileController(UserQueryMapper userQueryMapper) {
        this.userQueryMapper = userQueryMapper;
    }

    @GetMapping
    public MerchantProfileResponse current(HttpServletRequest request) {
        return toResponse(requireMerchant(request));
    }

    @PutMapping
    public MerchantProfileResponse update(@RequestBody MerchantProfileRequest request, HttpServletRequest httpRequest) {
        User user = requireMerchant(httpRequest);
        if ("BANNED".equalsIgnoreCase(user.getMerchantStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Banned merchant cannot edit profile");
        }

        user.setMerchantStoreName(normalize(request.getStoreName(), 120));
        user.setMerchantContactName(normalize(request.getContactName(), 80));
        user.setMerchantContactPhone(normalize(request.getContactPhone(), 30));
        user.setMerchantBusinessAddress(normalize(request.getBusinessAddress(), 255));
        user.setMerchantLicenseNumber(normalize(request.getLicenseNumber(), 80));
        user.setMerchantDescription(normalize(request.getDescription(), 500));

        if (Boolean.TRUE.equals(request.getSubmitForReview())) {
            validateRequired(user);
            if (!"APPROVED".equalsIgnoreCase(user.getMerchantStatus())) {
                user.setMerchantStatus("PENDING");
            }
        } else if (user.getMerchantStatus() == null || user.getMerchantStatus().isBlank() || "NONE".equalsIgnoreCase(user.getMerchantStatus())) {
            user.setMerchantStatus("UNREVIEWED");
        }

        userQueryMapper.update(user);
        return toResponse(user);
    }

    private User requireMerchant(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("CURRENT_USER");
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (!"MERCHANT".equalsIgnoreCase(currentUser.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant permission required");
        }
        User user = userQueryMapper.selectById(currentUser.getId());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found");
        }
        return user;
    }

    private void validateRequired(User user) {
        if (isBlank(user.getMerchantStoreName())
                || isBlank(user.getMerchantContactName())
                || isBlank(user.getMerchantContactPhone())
                || isBlank(user.getMerchantBusinessAddress())
                || isBlank(user.getMerchantLicenseNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please complete merchant basic information before submission");
        }
    }

    private MerchantProfileResponse toResponse(User user) {
        return new MerchantProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getMerchantStatus(),
                user.getMerchantStoreName(),
                user.getMerchantContactName(),
                user.getMerchantContactPhone(),
                user.getMerchantBusinessAddress(),
                user.getMerchantLicenseNumber(),
                user.getMerchantDescription()
        );
    }

    private String normalize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
