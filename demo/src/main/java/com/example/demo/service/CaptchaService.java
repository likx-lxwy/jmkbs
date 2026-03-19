package com.example.demo.service;

import com.example.demo.dto.CaptchaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CaptchaService {

    private static final long EXPIRE_SECONDS = 180;
    private static final int CAPTCHA_LENGTH = 4;
    private static final String CAPTCHA_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int SVG_WIDTH = 132;
    private static final int SVG_HEIGHT = 44;

    private final Map<String, CaptchaEntry> captchas = new ConcurrentHashMap<>();

    public CaptchaResponse createCaptcha() {
        cleanupExpired();
        String captchaText = randomText();
        String captchaToken = UUID.randomUUID().toString().replace("-", "");
        captchas.put(captchaToken, new CaptchaEntry(captchaText, LocalDateTime.now().plusSeconds(EXPIRE_SECONDS)));
        return new CaptchaResponse(captchaToken, svgDataUri(buildSvg(captchaText)), EXPIRE_SECONDS);
    }

    public void validateCaptcha(String captchaToken, String captchaCode) {
        cleanupExpired();
        if (isBlank(captchaToken) || isBlank(captchaCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入图片验证码");
        }

        CaptchaEntry entry = captchas.remove(captchaToken.trim());
        if (entry == null || entry.expiresAt.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码已过期，请刷新后重试");
        }
        if (!entry.code.equalsIgnoreCase(captchaCode.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码错误");
        }
    }

    private void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        captchas.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
    }

    private String randomText() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder builder = new StringBuilder(CAPTCHA_LENGTH);
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            builder.append(CAPTCHA_CHARS.charAt(random.nextInt(CAPTCHA_CHARS.length())));
        }
        return builder.toString();
    }

    private String buildSvg(String captchaText) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns='http://www.w3.org/2000/svg' width='").append(SVG_WIDTH)
                .append("' height='").append(SVG_HEIGHT)
                .append("' viewBox='0 0 ").append(SVG_WIDTH).append(' ').append(SVG_HEIGHT).append("'>");
        svg.append("<rect width='100%' height='100%' rx='10' ry='10' fill='#f8fafc'/>");

        for (int i = 0; i < 6; i++) {
            svg.append("<line x1='").append(random.nextInt(SVG_WIDTH)).append("' y1='").append(random.nextInt(SVG_HEIGHT))
                    .append("' x2='").append(random.nextInt(SVG_WIDTH)).append("' y2='").append(random.nextInt(SVG_HEIGHT))
                    .append("' stroke='").append(randomColor(0.25)).append("' stroke-width='1.2'/>");
        }

        for (int i = 0; i < 12; i++) {
            svg.append("<circle cx='").append(random.nextInt(SVG_WIDTH)).append("' cy='").append(random.nextInt(SVG_HEIGHT))
                    .append("' r='").append(1 + random.nextInt(3))
                    .append("' fill='").append(randomColor(0.18)).append("'/>");
        }

        for (int i = 0; i < captchaText.length(); i++) {
            int x = 18 + i * 27 + random.nextInt(-2, 3);
            int y = 30 + random.nextInt(-4, 5);
            int rotate = random.nextInt(-18, 19);
            svg.append("<text x='").append(x)
                    .append("' y='").append(y)
                    .append("' fill='").append(randomColor(0.95))
                    .append("' font-size='24' font-weight='700' font-family='Verdana,monospace'")
                    .append(" transform='rotate(").append(rotate).append(' ').append(x).append(' ').append(y).append(")'>")
                    .append(captchaText.charAt(i))
                    .append("</text>");
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private String randomColor(double alpha) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int red = random.nextInt(40, 190);
        int green = random.nextInt(60, 180);
        int blue = random.nextInt(80, 200);
        return "rgba(" + red + "," + green + "," + blue + "," + alpha + ")";
    }

    private String svgDataUri(String svg) {
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class CaptchaEntry {
        private final String code;
        private final LocalDateTime expiresAt;

        private CaptchaEntry(String code, LocalDateTime expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }
}
