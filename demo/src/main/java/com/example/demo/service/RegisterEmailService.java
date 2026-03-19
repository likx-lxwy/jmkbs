package com.example.demo.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class RegisterEmailService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final long EXPIRE_SECONDS = 300;
    private static final long COOLDOWN_SECONDS = 60;

    private final String neteaseUsername;
    private final String neteasePassword;
    private final String qqUsername;
    private final String qqPassword;
    private final Map<String, EmailCodeEntry> codes = new ConcurrentHashMap<>();

    public RegisterEmailService(@Value("${app.mail.netease.username:}") String neteaseUsername,
                                @Value("${app.mail.netease.password:}") String neteasePassword,
                                @Value("${app.mail.qq.username:}") String qqUsername,
                                @Value("${app.mail.qq.password:}") String qqPassword) {
        this.neteaseUsername = neteaseUsername == null ? "" : neteaseUsername.trim();
        this.neteasePassword = neteasePassword == null ? "" : neteasePassword.trim();
        this.qqUsername = qqUsername == null ? "" : qqUsername.trim();
        this.qqPassword = qqPassword == null ? "" : qqPassword.trim();
    }

    public Map<String, Object> sendRegisterCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        validateEmail(normalizedEmail);
        cleanupExpired();

        EmailCodeEntry existing = codes.get(normalizedEmail);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null && existing.nextSendAt.isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码发送过于频繁，请稍后再试");
        }

        MailSenderConfig senderConfig = selectSender(normalizedEmail);
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        sendMail(senderConfig, normalizedEmail, code);
        codes.put(normalizedEmail, new EmailCodeEntry(code, now.plusSeconds(EXPIRE_SECONDS), now.plusSeconds(COOLDOWN_SECONDS)));

        return Map.of(
                "expiresInSeconds", EXPIRE_SECONDS,
                "cooldownSeconds", COOLDOWN_SECONDS
        );
    }

    public void verifyRegisterCode(String email, String emailCode) {
        String normalizedEmail = normalizeEmail(email);
        validateEmail(normalizedEmail);
        if (emailCode == null || emailCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入邮箱验证码");
        }

        cleanupExpired();
        EmailCodeEntry entry = codes.remove(normalizedEmail);
        if (entry == null || entry.expiresAt.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱验证码已过期，请重新获取");
        }
        if (!entry.code.equals(emailCode.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱验证码错误");
        }
    }

    private void ensureEmailAvailable(String email) {
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的邮箱地址");
        }
    }

    private void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        codes.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
    }

    private MailSenderConfig selectSender(String recipientEmail) {
        MailSenderConfig netease = new MailSenderConfig(neteaseUsername, neteasePassword);
        MailSenderConfig qq = new MailSenderConfig(qqUsername, qqPassword);
        boolean preferQq = recipientEmail.endsWith("@qq.com");
        boolean preferNetease = recipientEmail.endsWith("@163.com") || recipientEmail.endsWith("@126.com") || recipientEmail.endsWith("@yeah.net");

        if (preferQq) {
            if (qq.isComplete()) {
                return qq;
            }
            if (netease.isComplete()) {
                return netease;
            }
        }
        if (preferNetease) {
            if (netease.isComplete()) {
                return netease;
            }
            if (qq.isComplete()) {
                return qq;
            }
        }
        if (netease.isComplete()) {
            return netease;
        }
        if (qq.isComplete()) {
            return qq;
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "邮箱服务未配置完整，请补充网易和QQ发件邮箱账号");
    }

    private void sendMail(MailSenderConfig senderConfig, String toEmail, String code) {
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(senderConfig.host());
            sender.setPort(465);
            sender.setUsername(senderConfig.username());
            sender.setPassword(senderConfig.password());
            sender.setDefaultEncoding("UTF-8");

            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");
            props.put("mail.smtp.writetimeout", "5000");

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderConfig.username());
            message.setTo(toEmail);
            message.setSubject("MK Menswear 注册邮箱验证码");
            message.setText("您的注册验证码为：" + code + "，5分钟内有效。如非本人操作，请忽略本邮件。");
            sender.send(message);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "验证码发送失败，请检查邮箱配置");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private record MailSenderConfig(String username, String password) {
        private boolean isComplete() {
            return username != null && !username.isBlank() && password != null && !password.isBlank();
        }

        private String host() {
            String normalized = username == null ? "" : username.trim().toLowerCase();
            if (normalized.endsWith("@qq.com")) {
                return "smtp.qq.com";
            }
            if (normalized.endsWith("@126.com")) {
                return "smtp.126.com";
            }
            if (normalized.endsWith("@yeah.net")) {
                return "smtp.yeah.net";
            }
            if (normalized.endsWith("@163.com")) {
                return "smtp.163.com";
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "暂不支持当前发件邮箱域名");
        }
    }

    private static class EmailCodeEntry {
        private final String code;
        private final LocalDateTime expiresAt;
        private final LocalDateTime nextSendAt;

        private EmailCodeEntry(String code, LocalDateTime expiresAt, LocalDateTime nextSendAt) {
            this.code = code;
            this.expiresAt = expiresAt;
            this.nextSendAt = nextSendAt;
        }
    }
}
