package com.example.demo.service;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.model.AuthToken;
import com.example.demo.model.User;
import com.example.demo.repository.AuthTokenRepository;
import com.example.demo.repository.LoginLogRepository;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final int SESSION_SECONDS = 7200;

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final LoginLogRepository loginLogRepository;

    public AuthService(UserRepository userRepository, AuthTokenRepository authTokenRepository, BCryptPasswordEncoder passwordEncoder, LoginLogRepository loginLogRepository) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginLogRepository = loginLogRepository;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpSession session, String ip) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseGet(() -> {
                    // 默认管理员兜底
                    if ("admin01".equalsIgnoreCase(request.getUsername())) {
                        User admin = new User();
                        admin.setUsername("admin01");
                        admin.setPassword(passwordEncoder.encode("admin123"));
                        admin.setRole("ADMIN");
                        admin.setMerchantStatus("NONE");
                        return userRepository.save(admin);
                    }
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordLogin(user, request.getUsername(), false, ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        if (user.getMerchantStatus() == null) {
            user.setMerchantStatus("NONE");
            userRepository.save(user);
        }

        AuthToken token = new AuthToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString().replace("-", ""));
        token.setExpiresAt(LocalDateTime.now().plusSeconds(SESSION_SECONDS));
        authTokenRepository.save(token);

        session.setAttribute("USER_ID", user.getId());
        session.setAttribute("USER_ROLE", user.getRole());
        session.setMaxInactiveInterval(SESSION_SECONDS);

        recordLogin(user, request.getUsername(), true, ip);
        return new LoginResponse(token.getToken(), user.getUsername(), user.getRole(), user.getMerchantStatus());
    }

    @Transactional
    public void logout(String token, HttpSession session) {
        if (token != null) {
            authTokenRepository.findByToken(token).ifPresent(authTokenRepository::delete);
        }
        session.invalidate();
    }

    @Transactional
    public User validateToken(String token, HttpSession session) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        authTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        AuthToken authToken = authTokenRepository.findByTokenWithUser(token).orElse(null);
        if (authToken == null || authToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        User user = authToken.getUser();
        // 预先触发加载，避免懒加载错误
        if (user != null) {
            user.getId();
            user.getRole();
            user.getMerchantStatus();
            user.getUsername();
        }
        session.setAttribute("USER_ID", user.getId());
        session.setAttribute("USER_ROLE", user.getRole());
        session.setMaxInactiveInterval(SESSION_SECONDS);
        authToken.setExpiresAt(LocalDateTime.now().plusSeconds(SESSION_SECONDS));
        authTokenRepository.save(authToken);
        return user;
    }

    @Transactional
    public LoginResponse register(RegisterRequest request, HttpSession session) {
        String role = request.getRole().toUpperCase();
        if (!role.equals("USER") && !role.equals("MERCHANT")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的角色");
        }
        userRepository.findByUsername(request.getUsername()).ifPresent(u -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        });

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setMerchantStatus(role.equals("MERCHANT") ? "PENDING" : "NONE");
        user.setWalletBalance(java.math.BigDecimal.valueOf(50));
        userRepository.save(user);

        // 自动登录
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(request.getUsername());
        loginRequest.setPassword(request.getPassword());
        return login(loginRequest, session, null);
    }

    @Transactional
    public void changePassword(HttpServletRequest request, String oldPassword, String newPassword) {
        User current = (User) request.getAttribute("CURRENT_USER");
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能为空");
        }
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "旧密码不能为空");
        }
        User user = userRepository.findById(current.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "旧密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private void recordLogin(User user, String username, boolean success, String ip) {
        com.example.demo.model.LoginLog log = new com.example.demo.model.LoginLog();
        log.setUser(user);
        log.setUsername(username);
        log.setIp(ip);
        log.setSuccess(success);
        loginLogRepository.save(log);
    }
}
