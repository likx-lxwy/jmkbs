package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpSession session) {
        String ip = httpRequest.getRemoteAddr();
        return authService.login(request, session, ip);
    }

    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request, HttpSession session) {
        return authService.register(request, session);
    }

    @PostMapping("/logout")
    public org.springframework.http.ResponseEntity<Void> logout(HttpServletRequest request, HttpSession session) {
        String token = request.getHeader("X-Auth-Token");
        authService.logout(token, session);
        return org.springframework.http.ResponseEntity.status(302)
                .header("Location", "/")
                .build();
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String oldPwd = body == null ? null : body.get("oldPassword");
        String newPwd = body == null ? null : body.get("newPassword");
        authService.changePassword(request, oldPwd, newPwd);
    }
}
