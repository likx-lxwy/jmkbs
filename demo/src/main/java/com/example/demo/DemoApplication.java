package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CommandLineRunner seedUsers(UserRepository userRepository, BCryptPasswordEncoder encoder) {
        return args -> {
            createIfMissing(userRepository, encoder, "user01", "user123", "USER", "NONE");
            createIfMissing(userRepository, encoder, "merchant01", "merchant123", "MERCHANT", "APPROVED");
            createIfMissing(userRepository, encoder, "admin01", "admin123", "ADMIN", "NONE");
        };
    }

    private void createIfMissing(UserRepository userRepository, BCryptPasswordEncoder encoder, String username, String rawPassword, String role, String merchantStatus) {
        userRepository.findByUsername(username).map(existing -> {
            boolean needsUpdate = false;
            if (!encoder.matches(rawPassword, existing.getPassword())) {
                existing.setPassword(encoder.encode(rawPassword));
                needsUpdate = true;
            }
            if (!role.equals(existing.getRole())) {
                existing.setRole(role);
                needsUpdate = true;
            }
            if (existing.getMerchantStatus() == null || !existing.getMerchantStatus().equals(merchantStatus)) {
                existing.setMerchantStatus(merchantStatus);
                needsUpdate = true;
            }
            return needsUpdate ? userRepository.save(existing) : existing;
        }).orElseGet(() -> {
            User u = new User();
            u.setUsername(username);
            u.setPassword(encoder.encode(rawPassword));
            u.setRole(role);
            u.setMerchantStatus(merchantStatus);
            return userRepository.save(u);
        });
    }
}
