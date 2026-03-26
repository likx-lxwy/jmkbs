package com.example.demo.controller;

import com.example.demo.mapper.PaymentLogQueryMapper;
import com.example.demo.mapper.UserQueryMapper;
import com.example.demo.model.PaymentLog;
import com.example.demo.model.User;
import com.example.demo.service.PasswordHashService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@CrossOrigin(origins = "*")
public class WalletController {

    private final UserQueryMapper userQueryMapper;
    private final PaymentLogQueryMapper paymentLogQueryMapper;
    private final PasswordHashService passwordHashService;

    public WalletController(UserQueryMapper userQueryMapper,
                            PaymentLogQueryMapper paymentLogQueryMapper,
                            PasswordHashService passwordHashService) {
        this.userQueryMapper = userQueryMapper;
        this.paymentLogQueryMapper = paymentLogQueryMapper;
        this.passwordHashService = passwordHashService;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        User user = currentUser(request);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("balance", user.getWalletBalance());
        return result;
    }

    @PostMapping("/recharge")
    public Map<String, Object> recharge(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User user = currentUser(request);
        ensureApprovedMerchant(user);
        BigDecimal amount = parseAmount(body.get("amount"));
        user.setWalletBalance(user.getWalletBalance().add(amount));
        userQueryMapper.update(user);
        saveLog(user, amount, "RECHARGE", "wallet recharge");
        return Map.of("balance", user.getWalletBalance());
    }

    @PostMapping("/admin/users/{id}/password")
    public void resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        User admin = currentUser(request);
        ensureAdmin(admin);
        String password = body.get("password");
        if (password == null || password.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is too short");
        }
        User target = userQueryMapper.selectById(id);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if ("DELETED".equalsIgnoreCase(target.getAccountStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deleted user cannot be updated");
        }
        target.setPassword(passwordHashService.encode(password));
        userQueryMapper.update(target);
    }

    private User currentUser(HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return user;
    }

    private void ensureAdmin(User user) {
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin permission required");
        }
    }

    private void ensureApprovedMerchant(User user) {
        if (user == null || !"MERCHANT".equalsIgnoreCase(user.getRole()) || !"APPROVED".equalsIgnoreCase(user.getMerchantStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only approved merchants can use wallet features");
        }
    }

    private BigDecimal parseAmount(Object obj) {
        if (obj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required");
        }
        try {
            BigDecimal amount = new BigDecimal(obj.toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than 0");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid amount format");
        }
    }

    private void saveLog(User user, BigDecimal amount, String type, String remark) {
        PaymentLog log = new PaymentLog();
        log.setUser(user);
        log.setAmount(amount);
        log.setType(type);
        log.setRemark(remark);
        paymentLogQueryMapper.insert(log);
    }
}
