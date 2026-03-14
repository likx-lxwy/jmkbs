package com.example.demo.controller;

import com.example.demo.model.PaymentLog;
import com.example.demo.model.User;
import com.example.demo.repository.PaymentLogRepository;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/wallet")
@CrossOrigin(origins = "*")
public class WalletController {

    private final UserRepository userRepository;
    private final PaymentLogRepository paymentLogRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public WalletController(UserRepository userRepository, PaymentLogRepository paymentLogRepository) {
        this.userRepository = userRepository;
        this.paymentLogRepository = paymentLogRepository;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        User user = currentUser(request);
        Map<String, Object> m = new HashMap<>();
        m.put("userId", user.getId());
        m.put("username", user.getUsername());
        m.put("balance", user.getWalletBalance());
        m.put("subscriptionPaidUntil", user.getSubscriptionPaidUntil());
        return m;
    }

    @PostMapping("/recharge")
    public Map<String, Object> recharge(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User user = currentUser(request);
        BigDecimal amount = parseAmount(body.get("amount"));
        user.setWalletBalance(user.getWalletBalance().add(amount));
        userRepository.save(user);
        saveLog(user, amount, "RECHARGE", "充值");
        return Map.of("balance", user.getWalletBalance());
    }

    @PostMapping("/subscribe")
    public Map<String, Object> subscribe(HttpServletRequest request) {
        User user = currentUser(request);
        if (!"MERCHANT".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅商家可缴纳开店费");
        }
        BigDecimal fee = BigDecimal.valueOf(500);
        if (user.getWalletBalance().compareTo(fee) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "余额不足，需至少¥500");
        }
        user.setWalletBalance(user.getWalletBalance().subtract(fee));
        LocalDate base = user.getSubscriptionPaidUntil();
        LocalDate start = (base != null && base.isAfter(LocalDate.now())) ? base : LocalDate.now();
        user.setSubscriptionPaidUntil(start.plusMonths(1));
        userRepository.save(user);
        saveLog(user, fee.negate(), "PAY", "开店费");
        User admin = userRepository.findFirstByRoleOrderByIdAsc("ADMIN").orElse(null);
        if (admin != null) {
            admin.setWalletBalance(admin.getWalletBalance().add(fee));
            userRepository.save(admin);
            saveLog(admin, fee, "INCOME", "开店费收入");
        }
        return Map.of("balance", user.getWalletBalance(), "subscriptionPaidUntil", user.getSubscriptionPaidUntil());
    }

    @GetMapping("/admin/users")
    public List<Map<String, Object>> allUsers(HttpServletRequest request) {
        User admin = currentUser(request);
        ensureAdmin(admin);
        return userRepository.findAll().stream()
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("role", u.getRole());
                    m.put("walletBalance", u.getWalletBalance());
                    return m;
                })
                .toList();
    }

    @PostMapping("/admin/users/{id}/wallet")
    public Map<String, Object> adjustWallet(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        User admin = currentUser(request);
        ensureAdmin(admin);
        BigDecimal amount = parseAmount(body.get("amount"));
        User target = userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        target.setWalletBalance(target.getWalletBalance().add(amount));
        userRepository.save(target);
        saveLog(target, amount, "ADJUST", "管理员调账");
        return Map.of("userId", target.getId(), "balance", target.getWalletBalance());
    }

    @PostMapping("/admin/users/{id}/password")
    public void resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        User admin = currentUser(request);
        ensureAdmin(admin);
        String password = body.get("password");
        if (password == null || password.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码太短");
        }
        User target = userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        target.setPassword(encoder.encode(password));
        userRepository.save(target);
    }

    private User currentUser(HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return user;
    }

    private void ensureAdmin(User user) {
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
    }

    private BigDecimal parseAmount(Object obj) {
        if (obj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "金额不能为空");
        }
        try {
            BigDecimal amt = new BigDecimal(obj.toString());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "金额必须大于0");
            }
            return amt;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "金额格式错误");
        }
    }

    private void saveLog(User user, BigDecimal amount, String type, String remark) {
        PaymentLog log = new PaymentLog();
        log.setUser(user);
        log.setAmount(amount);
        log.setType(type);
        log.setRemark(remark);
        paymentLogRepository.save(log);
    }
}
