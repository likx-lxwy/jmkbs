package com.example.demo.controller;

import com.example.demo.model.ChatMessage;
import com.example.demo.model.Product;
import com.example.demo.model.User;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public ChatController(ChatMessageRepository chatMessageRepository, UserRepository userRepository, ProductRepository productRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @GetMapping
    public List<ChatMessage> list(@RequestParam Long productId, @RequestParam Long targetId, HttpServletRequest request) {
        User user = currentUser(request);
        return chatMessageRepository.findConversation(productId, user.getId(), targetId);
    }

    @GetMapping("/recent")
    public List<ChatMessage> recent(HttpServletRequest request) {
        User user = currentUser(request);
        return chatMessageRepository.findRecent(user.getId());
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
        Product product = productRepository.findById(productId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        User receiver = userRepository.findById(targetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "接收方不存在"));

        ChatMessage msg = new ChatMessage();
        msg.setSender(sender);
        msg.setReceiver(receiver);
        msg.setProduct(product);
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        return chatMessageRepository.save(msg);
    }

    private User currentUser(HttpServletRequest request) {
        User u = (User) request.getAttribute("CURRENT_USER");
        if (u == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return u;
    }

    private Long parseLong(Object obj, String msg) {
        if (obj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        }
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        }
    }
}
