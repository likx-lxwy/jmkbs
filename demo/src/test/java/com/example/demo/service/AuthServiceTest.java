package com.example.demo.service;

import com.example.demo.dto.LoginRequest;
import com.example.demo.mapper.UserQueryMapper;
import com.example.demo.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserQueryMapper userQueryMapper;

    @Mock
    private PasswordHashService passwordHashService;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private CaptchaService captchaService;

    @Mock
    private RegisterEmailService registerEmailService;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginRejectsBannedAccount() {
        User user = new User();
        user.setUsername("user01");
        user.setPassword("encoded-password");
        user.setRole("USER");
        user.setAccountStatus("BANNED");

        LoginRequest request = new LoginRequest();
        request.setUsername("user01");
        request.setPassword("user123");
        request.setCaptchaToken("token");
        request.setCaptchaCode("code");

        when(userQueryMapper.selectByUsername("user01")).thenReturn(user);
        when(passwordHashService.matches("user123", "encoded-password")).thenReturn(true);
        when(passwordHashService.needsUpgrade("encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("账号已被封禁");

        verify(jwtTokenService, never()).createToken(user);
    }

    @Test
    void loginRejectsDeletedAccount() {
        User user = new User();
        user.setUsername("user01");
        user.setPassword("encoded-password");
        user.setRole("USER");
        user.setAccountStatus("DELETED");

        LoginRequest request = new LoginRequest();
        request.setUsername("user01");
        request.setPassword("user123");
        request.setCaptchaToken("token");
        request.setCaptchaCode("code");

        when(userQueryMapper.selectByUsername("user01")).thenReturn(user);
        when(passwordHashService.matches("user123", "encoded-password")).thenReturn(true);
        when(passwordHashService.needsUpgrade("encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("账号已被删除");

        verify(jwtTokenService, never()).createToken(user);
    }
}
