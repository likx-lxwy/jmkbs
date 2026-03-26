package com.example.demo.service;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordHashService {

    private final BCryptPasswordEncoder legacyBcryptEncoder = new BCryptPasswordEncoder();

    public String encode(String rawPassword) {
        return rawPassword == null ? null : DigestUtils.md5Hex(rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        if (isMd5Hash(storedPassword)) {
            return storedPassword.equalsIgnoreCase(encode(rawPassword));
        }
        if (isBcryptHash(storedPassword)) {
            return legacyBcryptEncoder.matches(rawPassword, storedPassword);
        }
        return false;
    }

    public boolean needsUpgrade(String storedPassword) {
        return !isMd5Hash(storedPassword);
    }

    private boolean isMd5Hash(String value) {
        return value != null && value.matches("(?i)^[a-f0-9]{32}$");
    }

    private boolean isBcryptHash(String value) {
        return value != null
                && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }
}
