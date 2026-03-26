package com.example.demo;

import com.example.demo.mapper.UserQueryMapper;
import com.example.demo.model.User;
import com.example.demo.service.PasswordHashService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@MapperScan("com.example.demo.mapper")
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedUsers(UserQueryMapper userQueryMapper, PasswordHashService passwordHashService) {
        return args -> {
            createIfMissing(userQueryMapper, passwordHashService, "user01", "user123", "USER", "NONE");
            createIfMissing(userQueryMapper, passwordHashService, "merchant01", "merchant123", "MERCHANT", "APPROVED");
            createIfMissing(userQueryMapper, passwordHashService, "admin01", "admin123", "ADMIN", "NONE");
        };
    }

    private void createIfMissing(UserQueryMapper userQueryMapper,
                                 PasswordHashService passwordHashService,
                                 String username,
                                 String rawPassword,
                                 String role,
                                 String merchantStatus) {
        User existing = userQueryMapper.selectByUsername(username);
        if (existing != null) {
            boolean needsUpdate = false;
            if (!passwordHashService.matches(rawPassword, existing.getPassword())
                    || passwordHashService.needsUpgrade(existing.getPassword())) {
                existing.setPassword(passwordHashService.encode(rawPassword));
                needsUpdate = true;
            }
            if (!role.equals(existing.getRole())) {
                existing.setRole(role);
                needsUpdate = true;
            }
            if (existing.getAccountStatus() == null || existing.getAccountStatus().isBlank()) {
                existing.setAccountStatus("ACTIVE");
                needsUpdate = true;
            }
            if (existing.getMerchantStatus() == null || !existing.getMerchantStatus().equals(merchantStatus)) {
                existing.setMerchantStatus(merchantStatus);
                needsUpdate = true;
            }
            if (needsUpdate) {
                userQueryMapper.update(existing);
            }
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordHashService.encode(rawPassword));
        user.setRole(role);
        user.setAccountStatus("ACTIVE");
        user.setMerchantStatus(merchantStatus);
        userQueryMapper.insert(user);
    }
}
