package com.example.demo.controller;

import com.example.demo.mapper.CustomerOrderQueryMapper;
import com.example.demo.mapper.RefundChatMessageQueryMapper;
import com.example.demo.model.CustomerOrder;
import com.example.demo.model.OrderItem;
import com.example.demo.model.Product;
import com.example.demo.model.RefundChatMessage;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders/{orderId}/refund-chat")
@CrossOrigin(origins = "*")
public class RefundChatController {

    private static final Set<String> ACTIVE_REFUND_STATUSES = Set.of("REFUND_REQUESTED", "REFUNDED");

    private final RefundChatMessageQueryMapper refundChatMessageQueryMapper;
    private final CustomerOrderQueryMapper customerOrderQueryMapper;

    public RefundChatController(RefundChatMessageQueryMapper refundChatMessageQueryMapper,
                                CustomerOrderQueryMapper customerOrderQueryMapper) {
        this.refundChatMessageQueryMapper = refundChatMessageQueryMapper;
        this.customerOrderQueryMapper = customerOrderQueryMapper;
    }

    @GetMapping
    public List<RefundChatMessage> list(@PathVariable Long orderId, HttpServletRequest request) {
        User currentUser = currentUser(request);
        CustomerOrder order = loadOrder(orderId);
        RefundParticipants participants = ensureAuthorizedParticipant(order, currentUser);
        ensureRefundChatAccessible(order, participants.existingCount());
        return refundChatMessageQueryMapper.findByOrderId(orderId);
    }

    @PostMapping
    public void send(@PathVariable Long orderId,
                     @RequestBody(required = false) Map<String, String> body,
                     HttpServletRequest request) {
        User currentUser = currentUser(request);
        CustomerOrder order = loadOrder(orderId);
        RefundParticipants participants = ensureAuthorizedParticipant(order, currentUser);
        ensureRefundChatAccessible(order, participants.existingCount());

        String content = body == null ? "" : Objects.toString(body.get("content"), "").trim();
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息内容不能为空");
        }

        RefundChatMessage message = new RefundChatMessage();
        message.setOrder(order);
        message.setSender(currentUser);
        message.setReceiver(participants.counterpartFor(currentUser));
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        refundChatMessageQueryMapper.insert(message);
    }

    private CustomerOrder loadOrder(Long orderId) {
        CustomerOrder order = customerOrderQueryMapper.selectById(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return order;
    }

    private RefundParticipants ensureAuthorizedParticipant(CustomerOrder order, User currentUser) {
        User buyer = order.getBuyer();
        if (buyer == null || buyer.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order buyer not found");
        }

        User merchant = resolveMerchant(order);
        Long currentUserId = currentUser.getId();
        if (!buyer.getId().equals(currentUserId) && !merchant.getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this refund chat");
        }

        long existingCount = refundChatMessageQueryMapper.countByOrderId(order.getId());
        return new RefundParticipants(buyer, merchant, existingCount);
    }

    private void ensureRefundChatAccessible(CustomerOrder order, long existingCount) {
        String status = normalizeStatus(order.getStatus());
        if (!ACTIVE_REFUND_STATUSES.contains(status) && existingCount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund chat is only available for after-sales orders");
        }
    }

    private User resolveMerchant(CustomerOrder order) {
        Set<User> merchants = order.getItems() == null
                ? Set.of()
                : order.getItems().stream()
                .map(OrderItem::getProduct)
                .filter(Objects::nonNull)
                .map(Product::getOwner)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (merchants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order merchant not found");
        }
        if (merchants.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mixed-merchant orders are not supported");
        }
        User merchant = merchants.iterator().next();
        if (merchant.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order merchant not found");
        }
        return merchant;
    }

    private User currentUser(HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return user;
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }

    private record RefundParticipants(User buyer, User merchant, long existingCount) {
        User counterpartFor(User currentUser) {
            return buyer.getId().equals(currentUser.getId()) ? merchant : buyer;
        }
    }
}
