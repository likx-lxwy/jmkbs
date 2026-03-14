package com.example.demo.controller;

import com.example.demo.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.codec.binary.Hex;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/terminal")
@CrossOrigin(origins = "*")
public class TerminalController {

    private static final String SECRET_HEX = "962e9179607aa152eb7bd0598381bc9d440f9006289d3c6c0f16ce95ba92c58f";
    private static final String PASSWORD = "7d3f2a8c-5b41-4e9f-9c2d-1a6b7c8d9e03";

    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
        String pass = body.getOrDefault("password", "");
        String sign = body.getOrDefault("signature", "");
        if (!verify(pass, sign)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "终端密钥校验失败");
        }
        String cmd = body.get("command");
        if (cmd == null || cmd.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "命令不能为空");
        }

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        ProcessBuilder pb = isWindows
                ? new ProcessBuilder("powershell.exe", "-Command", cmd)
                : new ProcessBuilder("bash", "-lc", cmd);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            boolean finished = p.waitFor(Duration.ofSeconds(8).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "执行超时");
            }
            String output;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                output = br.lines().reduce("", (a, b) -> a + b + "\n");
            }
            return Map.of("exitCode", p.exitValue(), "output", output);
        } catch (IOException | InterruptedException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "执行失败", e);
        }
    }

    private boolean verify(String password, String signature) {
        try {
            byte[] key = Hex.decodeHex(SECRET_HEX);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] expected = mac.doFinal(PASSWORD.getBytes(StandardCharsets.UTF_8));
            byte[] provided = mac.doFinal(password.getBytes(StandardCharsets.UTF_8));
            String expHex = Hex.encodeHexString(expected);
            String provHex = Hex.encodeHexString(provided);
            // 要求密码正确且 HMAC 匹配
            return PASSWORD.equals(password) && expHex.equals(provHex) && signature != null && signature.equals(provHex);
        } catch (Exception e) {
            return false;
        }
    }
}
