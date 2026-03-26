package com.example.demo.controller;

import com.example.demo.mapper.ChatMessageQueryMapper;
import com.example.demo.mapper.ProductQueryMapper;
import com.example.demo.mapper.UserQueryMapper;
import com.example.demo.model.ChatMessage;
import com.example.demo.model.Product;
import com.example.demo.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatMessageQueryMapper chatMessageQueryMapper;
    private final UserQueryMapper userQueryMapper;
    private final ProductQueryMapper productQueryMapper;

    public ChatController(ChatMessageQueryMapper chatMessageQueryMapper,
                          UserQueryMapper userQueryMapper,
                          ProductQueryMapper productQueryMapper) {
        this.chatMessageQueryMapper = chatMessageQueryMapper;
        this.userQueryMapper = userQueryMapper;
        this.productQueryMapper = productQueryMapper;
    }

    @GetMapping
    public List<ChatMessage> list(@RequestParam Long productId, @RequestParam Long targetId, HttpServletRequest request) {
        User user = currentUser(request);
        return chatMessageQueryMapper.findConversation(productId, user.getId(), targetId);
    }

    @GetMapping("/recent")
    public List<ChatMessage> recent(HttpServletRequest request) {
        User user = currentUser(request);
        return chatMessageQueryMapper.findRecent(user.getId());
    }

    @PostMapping
    public ChatMessage send(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User sender = currentUser(request);
        Long productId = parseLong(body.get("productId"), "缺少商品");
        Long targetId = parseLong(body.get("targetId"), "缺少接收方");
        String content = body.get("content") == null ? "" : body.get("content").toString().trim();
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息不能为空");
        }

        Product product = productQueryMapper.selectById(productId);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        User receiver = userQueryMapper.selectById(targetId);
        if (receiver == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "接收方不存在");
        }

        ChatMessage message = new ChatMessage();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setProduct(product);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageQueryMapper.insert(message);
        return message;
    }

    private User currentUser(HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return user;
    }

    private Long parseLong(Object value, String message) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
