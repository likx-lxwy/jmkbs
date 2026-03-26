package com.example.demo.service;

import com.example.demo.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JwtTokenService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String HEADER_PART = URL_ENCODER.encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
    private static final Pattern UID_PATTERN = Pattern.compile("\"uid\"\\s*:\\s*(\\d+)");
    private static final Pattern EXP_PATTERN = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("\"username\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
    private static final Pattern ROLE_PATTERN = Pattern.compile("\"role\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
    private static final Pattern MERCHANT_STATUS_PATTERN = Pattern.compile("\"merchantStatus\"\\s*:\\s*(null|\"((?:\\\\.|[^\"])*)\")");

    private final byte[] secretKey;
    private final long ttlSeconds;

    public JwtTokenService(@Value("${app.jwt.secret:mk-menswear-jwt-secret-change-me-please}") String secret,
                           @Value("${app.jwt.ttl-seconds:7200}") long ttlSeconds) {
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public String createToken(User user) {
        long now = Instant.now().getEpochSecond();
        String payloadJson = buildPayloadJson(user, now, now + ttlSeconds);
        String payloadPart = URL_ENCODER.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String unsignedToken = HEADER_PART + "." + payloadPart;
        return unsignedToken + "." + sign(unsignedToken);
    }

    public JwtClaims parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Missing token");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid token format");
        }
        if (!HEADER_PART.equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported token header");
        }

        String unsignedToken = parts[0] + "." + parts[1];
        String expectedSignature = sign(unsignedToken);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid token signature");
        }

        String payloadJson;
        try {
            payloadJson = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid token payload", ex);
        }

        long expiresAt = extractLong(payloadJson, EXP_PATTERN, "exp");
        if (expiresAt <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("Token expired");
        }

        return new JwtClaims(
                extractLong(payloadJson, UID_PATTERN, "uid"),
                extractString(payloadJson, USERNAME_PATTERN, "username"),
                extractString(payloadJson, ROLE_PATTERN, "role"),
                extractNullableString(payloadJson)
        );
    }

    private String buildPayloadJson(User user, long issuedAt, long expiresAt) {
        String merchantStatus = user.getMerchantStatus() == null
                ? "null"
                : "\"" + escape(user.getMerchantStatus()) + "\"";
        return "{"
                + "\"sub\":\"" + escape(String.valueOf(user.getId())) + "\","
                + "\"uid\":" + user.getId() + ","
                + "\"username\":\"" + escape(user.getUsername()) + "\","
                + "\"role\":\"" + escape(user.getRole()) + "\","
                + "\"merchantStatus\":" + merchantStatus + ","
                + "\"iat\":" + issuedAt + ","
                + "\"exp\":" + expiresAt
                + "}";
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }

    private long extractLong(String payloadJson, Pattern pattern, String claimName) {
        Matcher matcher = pattern.matcher(payloadJson);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing token claim: " + claimName);
        }
        return Long.parseLong(matcher.group(1));
    }

    private String extractString(String payloadJson, Pattern pattern, String claimName) {
        Matcher matcher = pattern.matcher(payloadJson);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing token claim: " + claimName);
        }
        return unescape(matcher.group(1));
    }

    private String extractNullableString(String payloadJson) {
        Matcher matcher = MERCHANT_STATUS_PATTERN.matcher(payloadJson);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing token claim: merchantStatus");
        }
        if ("null".equals(matcher.group(1))) {
            return null;
        }
        return unescape(matcher.group(2));
    }

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String unescape(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public record JwtClaims(long userId, String username, String role, String merchantStatus) {
    }
}
