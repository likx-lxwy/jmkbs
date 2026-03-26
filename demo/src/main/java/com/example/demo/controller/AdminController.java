package com.example.demo.controller;

import com.example.demo.dto.AdminMerchantResponse;
import com.example.demo.dto.AdminOrderSummary;
import com.example.demo.dto.AdminOverviewResponse;
import com.example.demo.dto.AdminUserResponse;
import com.example.demo.dto.UpdateMerchantStatusRequest;
import com.example.demo.dto.UpdateUserAccountStatusRequest;
import com.example.demo.mapper.CustomerOrderQueryMapper;
import com.example.demo.mapper.PaymentLogQueryMapper;
import com.example.demo.mapper.ProductQueryMapper;
import com.example.demo.mapper.UserQueryMapper;
import com.example.demo.model.CustomerOrder;
import com.example.demo.model.PaymentLog;
import com.example.demo.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final UserQueryMapper userQueryMapper;
    private final CustomerOrderQueryMapper customerOrderQueryMapper;
    private final ProductQueryMapper productQueryMapper;
    private final PaymentLogQueryMapper paymentLogQueryMapper;

    public AdminController(UserQueryMapper userQueryMapper,
                           CustomerOrderQueryMapper customerOrderQueryMapper,
                           ProductQueryMapper productQueryMapper,
                           PaymentLogQueryMapper paymentLogQueryMapper) {
        this.userQueryMapper = userQueryMapper;
        this.customerOrderQueryMapper = customerOrderQueryMapper;
        this.productQueryMapper = productQueryMapper;
        this.paymentLogQueryMapper = paymentLogQueryMapper;
    }

    @GetMapping("/overview")
    public AdminOverviewResponse overview(HttpServletRequest request) {
        ensureAdmin(request);
        return new AdminOverviewResponse(
                userQueryMapper.countAll(),
                userQueryMapper.countByRole("MERCHANT"),
                userQueryMapper.countByRoleAndMerchantStatus("MERCHANT", "PENDING"),
                userQueryMapper.countByRoleAndMerchantStatus("MERCHANT", "APPROVED"),
                customerOrderQueryMapper.countAll(),
                customerOrderQueryMapper.sumTotalAmount(),
                productQueryMapper.countAll(),
                productQueryMapper.countByStockLessThanEqual(5)
        );
    }

    @GetMapping("/merchants")
    public List<AdminMerchantResponse> merchants(@RequestParam(required = false) String status, HttpServletRequest request) {
        ensureAdmin(request);
        List<User> merchants;
        if (status != null && !status.isBlank()) {
            merchants = userQueryMapper.selectByRoleAndMerchantStatusOrderByIdDesc("MERCHANT", status.toUpperCase(Locale.ROOT));
        } else {
            merchants = userQueryMapper.selectByRoleOrderByIdDesc("MERCHANT");
        }
        return merchants.stream()
                .map(user -> new AdminMerchantResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getRole(),
                        user.getMerchantStatus(),
                        user.getMerchantStoreName(),
                        user.getMerchantContactName(),
                        user.getMerchantContactPhone(),
                        user.getMerchantBusinessAddress(),
                        user.getMerchantLicenseNumber(),
                        user.getMerchantDescription()))
                .toList();
    }

    @PostMapping("/merchants/{id}/status")
    public AdminMerchantResponse updateMerchantStatus(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateMerchantStatusRequest request,
                                                      HttpServletRequest httpRequest) {
        ensureAdmin(httpRequest);
        String status = request.getStatus() == null ? "" : request.getStatus().toUpperCase(Locale.ROOT);
        if (!List.of("APPROVED", "PENDING", "UNREVIEWED", "BANNED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported merchant status");
        }

        User user = userQueryMapper.selectById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (!"MERCHANT".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target user is not a merchant");
        }

        user.setMerchantStatus(status);
        userQueryMapper.update(user);
        return new AdminMerchantResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getMerchantStatus(),
                user.getMerchantStoreName(),
                user.getMerchantContactName(),
                user.getMerchantContactPhone(),
                user.getMerchantBusinessAddress(),
                user.getMerchantLicenseNumber(),
                user.getMerchantDescription()
        );
    }

    @GetMapping("/orders")
    public List<AdminOrderSummary> orders(HttpServletRequest request) {
        ensureAdmin(request);
        return customerOrderQueryMapper.selectAllOrderByCreatedAtDesc()
                .stream()
                .map(this::toOrderSummary)
                .toList();
    }

    @GetMapping("/revenue")
    public List<Map<String, Object>> revenue(HttpServletRequest request) {
        ensureAdmin(request);
        List<PaymentLog> logs = paymentLogQueryMapper.findAdminIncome();
        Map<Long, BigDecimal> currentBalanceByUserId = new HashMap<>();
        Map<Long, BigDecimal> newerAmountByUserId = new HashMap<>();

        return logs.stream()
                .map(log -> {
                    Long userId = log.getUser() == null ? null : log.getUser().getId();
                    BigDecimal currentBalance = currentBalanceByUserId.computeIfAbsent(
                            userId,
                            ignored -> log.getUser() == null || log.getUser().getWalletBalance() == null
                                    ? BigDecimal.ZERO
                                    : log.getUser().getWalletBalance()
                    );
                    BigDecimal newerAmount = newerAmountByUserId.getOrDefault(userId, BigDecimal.ZERO);
                    BigDecimal balanceAfter = currentBalance.subtract(newerAmount);

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", log.getId());
                    result.put("orderNumber", log.getOrderNumber());
                    result.put("amount", log.getAmount());
                    result.put("type", log.getType());
                    result.put("remark", log.getRemark());
                    result.put("createdAt", log.getCreatedAt());
                    result.put("balanceAfter", balanceAfter);

                    newerAmountByUserId.put(userId, newerAmount.add(log.getAmount() == null ? BigDecimal.ZERO : log.getAmount()));
                    return result;
                })
                .toList();
    }

    @GetMapping("/users")
    public List<AdminUserResponse> users(HttpServletRequest request) {
        ensureAdmin(request);
        return userQueryMapper.selectAllIncludingDeletedOrderByIdDesc()
                .stream()
                .map(this::toUserResponse)
                .toList();
    }

    @PostMapping("/users/{id}/account-status")
    public AdminUserResponse updateUserAccountStatus(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateUserAccountStatusRequest request,
                                                     HttpServletRequest httpRequest) {
        User admin = ensureAdmin(httpRequest);
        User user = requireManageableUser(id);

        if (admin.getId() != null && admin.getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能修改自己的账号状态");
        }
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能封禁管理员账号");
        }
        if ("DELETED".equalsIgnoreCase(user.getAccountStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已删除账号不能再修改状态");
        }

        String status = request.getStatus() == null ? "" : request.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "BANNED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported account status");
        }

        user.setAccountStatus(status);
        userQueryMapper.update(user);
        return toUserResponse(user);
    }

    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable Long id, HttpServletRequest request) {
        User admin = ensureAdmin(request);
        User user = requireManageableUser(id);

        if (admin.getId() != null && admin.getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能删除自己的账号");
        }
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能删除管理员账号");
        }
        if ("DELETED".equalsIgnoreCase(user.getAccountStatus())) {
            return;
        }

        user.setAccountStatus("DELETED");
        userQueryMapper.update(user);
    }

    private AdminOrderSummary toOrderSummary(CustomerOrder order) {
        return new AdminOrderSummary(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getStatus(),
                order.getPayMethod(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getItems() == null ? 0 : order.getItems().size()
        );
    }

    private AdminUserResponse toUserResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getAccountStatus(),
                user.getMerchantStatus(),
                user.getMerchantStoreName(),
                user.getWalletBalance()
        );
    }

    private User requireManageableUser(Long id) {
        User user = userQueryMapper.selectById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private User ensureAdmin(HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin permission required");
        }
        return user;
    }
}
