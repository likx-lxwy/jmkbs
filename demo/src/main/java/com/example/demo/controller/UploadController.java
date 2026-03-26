package com.example.demo.controller;

import com.example.demo.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB
    private static final long MAX_VIDEO_SIZE = 50 * 1024 * 1024; // 50MB

    @PostMapping("/image")
    public Map<String, String> uploadImage(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后再上传");
        }
        if (!(isAllowedMerchant(user) || isAdmin(user))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员或商家可上传图片");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件过大（上限5MB）");
        }
        try (InputStream in = file.getInputStream()) {
            BufferedImage source = ImageIO.read(in);
            if (source == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的图片格式");
            }
            int minSide = Math.min(source.getWidth(), source.getHeight());
            int x = (source.getWidth() - minSide) / 2;
            int y = (source.getHeight() - minSide) / 2;
            BufferedImage square = source.getSubimage(x, y, minSide, minSide);

            BufferedImage output = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = output.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.drawImage(square, 0, 0, 400, 400, null);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(output, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            String dataUrl = "data:image/png;base64," + base64;
            log.info("用户 {} 上传图片为 base64，长度={}", user.getUsername(), base64.length());
            return Map.of("url", dataUrl);
        } catch (IOException e) {
            log.error("上传失败，用户{}，文件名={}", user.getUsername(), file.getOriginalFilename(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "上传失败", e);
        }
    }

    @PostMapping("/video")
    public Map<String, String> uploadVideo(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录再上传");
        }
        if (!(isAllowedMerchant(user) || isAdmin(user))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员或商家可上传视频");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "视频过大（上限50MB）");
        }
        try {
            String mime = file.getContentType();
            if (mime == null || !mime.startsWith("video/")) {
                mime = "video/mp4";
            }
            try (InputStream in = file.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                String base64 = Base64.getEncoder().encodeToString(bytes);
                String dataUrl = "data:" + mime + ";base64," + base64;
                log.info("用户 {} 上传视频为 base64，长度={}", user.getUsername(), base64.length());
                return Map.of("url", dataUrl);
            }
        } catch (IOException e) {
            log.error("上传视频失败，用户{}，文件名={}", user.getUsername(), file.getOriginalFilename(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "上传失败", e);
        }
    }

    private boolean isAllowedMerchant(User user) {
        return "MERCHANT".equalsIgnoreCase(user.getRole())
                && "APPROVED".equalsIgnoreCase(user.getMerchantStatus());
    }

    private boolean isAdmin(User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole());
    }
}
