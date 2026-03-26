package com.example.demo.config;

import com.example.demo.model.User;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final Set<String> openPaths = Set.of(
            "/api/auth/captcha",
            "/api/auth/email-code",
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/logout",
            "/api/categories",
            "/api/payments/alipay/notify",
            "/api/payments/alipay/return"
    );

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        if (isOpenPath(path, request.getMethod())) {
            return true;
        }

        User user = authService.validateToken(resolveToken(request));
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        authService.ensureAccountAccessible(user);

        request.setAttribute("CURRENT_USER", user);
        return true;
    }

    private boolean isOpenPath(String path, String method) {
        if ("GET".equalsIgnoreCase(method)) {
            if (path.matches("^/api/products/?$") || path.matches("^/api/products/\\d+$")) {
                return true;
            }
        }
        return openPaths.contains(path);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return request.getHeader("X-Auth-Token");
    }
}
