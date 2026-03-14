package com.example.demo.config;

import com.example.demo.model.User;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final Set<String> openPaths = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/logout",
            "/api/categories"
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
        String token = request.getHeader("X-Auth-Token");
        HttpSession session = request.getSession();
        User user = authService.validateToken(token, session);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或会话已过期");
        }
        request.setAttribute("CURRENT_USER", user);
        return true;
    }

    private boolean isOpenPath(String path, String method) {
        if ("GET".equalsIgnoreCase(method)) {
            // 商品列表/详情允许匿名，其它子路径（如 /mine）需鉴权
            if (path.matches("^/api/products/?$") || path.matches("^/api/products/\\d+$")) {
                return true;
            }
        }
        return openPaths.contains(path);
    }
}
