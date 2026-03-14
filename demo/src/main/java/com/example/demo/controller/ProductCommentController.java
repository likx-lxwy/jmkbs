package com.example.demo.controller;

import com.example.demo.model.Product;
import com.example.demo.model.ProductComment;
import com.example.demo.model.User;
import com.example.demo.repository.ProductCommentRepository;
import com.example.demo.repository.ProductRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products/{productId}/comments")
@CrossOrigin(origins = "*")
public class ProductCommentController {

    private final ProductCommentRepository commentRepository;
    private final ProductRepository productRepository;

    public ProductCommentController(ProductCommentRepository commentRepository, ProductRepository productRepository) {
        this.commentRepository = commentRepository;
        this.productRepository = productRepository;
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable Long productId) {
        return commentRepository.findByProductIdOrderByCreatedAtAsc(productId)
                .stream()
                .map(this::toMap)
                .toList();
    }

    @PostMapping
    public Map<String, Object> add(@PathVariable Long productId, @RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        String content = body == null ? null : body.get("content");
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内容不能为空");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        ProductComment c = new ProductComment();
        c.setProduct(product);
        c.setUser(user);
        c.setContent(content.trim());
        c.setCreatedAt(LocalDateTime.now());
        return toMap(commentRepository.save(c));
    }

    private Map<String, Object> toMap(ProductComment c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("content", c.getContent());
        m.put("createdAt", c.getCreatedAt());
        if (c.getUser() != null) {
            Map<String, Object> u = new HashMap<>();
            u.put("id", c.getUser().getId());
            u.put("username", c.getUser().getUsername());
            u.put("role", c.getUser().getRole());
            u.put("merchantStatus", c.getUser().getMerchantStatus());
            m.put("user", u);
        }
        return m;
    }
}

