package com.example.demo.controller;

import com.example.demo.mapper.ProductCommentQueryMapper;
import com.example.demo.mapper.ProductQueryMapper;
import com.example.demo.model.Product;
import com.example.demo.model.ProductComment;
import com.example.demo.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products/{productId}/comments")
@CrossOrigin(origins = "*")
public class ProductCommentController {

    private final ProductCommentQueryMapper productCommentQueryMapper;
    private final ProductQueryMapper productQueryMapper;

    public ProductCommentController(ProductCommentQueryMapper productCommentQueryMapper,
                                    ProductQueryMapper productQueryMapper) {
        this.productCommentQueryMapper = productCommentQueryMapper;
        this.productQueryMapper = productQueryMapper;
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable Long productId) {
        return productCommentQueryMapper.findByProductIdOrderByCreatedAtAsc(productId)
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

        Product product = productQueryMapper.selectById(productId);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }

        ProductComment comment = new ProductComment();
        comment.setProduct(product);
        comment.setUser(user);
        comment.setContent(content.trim());
        comment.setCreatedAt(LocalDateTime.now());
        productCommentQueryMapper.insert(comment);
        return toMap(comment);
    }

    private Map<String, Object> toMap(ProductComment comment) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", comment.getId());
        result.put("content", comment.getContent());
        result.put("createdAt", comment.getCreatedAt());
        if (comment.getUser() != null) {
            Map<String, Object> user = new HashMap<>();
            user.put("id", comment.getUser().getId());
            user.put("username", comment.getUser().getUsername());
            user.put("role", comment.getUser().getRole());
            user.put("merchantStatus", comment.getUser().getMerchantStatus());
            result.put("user", user);
        }
        return result;
    }
}
