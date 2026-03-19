package com.example.demo.controller;

import com.example.demo.dto.AdminMerchantResponse;
import com.example.demo.dto.AdminOrderSummary;
import com.example.demo.dto.AdminOverviewResponse;
import com.example.demo.dto.UpdateMerchantStatusRequest;
import com.example.demo.model.AppSetting;
import com.example.demo.model.CustomerOrder;
import com.example.demo.model.LoginLog;
import com.example.demo.model.User;
import com.example.demo.repository.AppSettingRepository;
import com.example.demo.repository.CustomerOrderRepository;
import com.example.demo.repository.LoginLogRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.PaymentLogRepository;
import com.example.demo.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final UserRepository userRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final ProductRepository productRepository;
    private final AppSettingRepository appSettingRepository;
    private final LoginLogRepository loginLogRepository;
    private final OrderService orderService;
    private final PaymentLogRepository paymentLogRepository;

    public AdminController(UserRepository userRepository, CustomerOrderRepository customerOrderRepository, ProductRepository productRepository, AppSettingRepository appSettingRepository, LoginLogRepository loginLogRepository, OrderService orderService, PaymentLogRepository paymentLogRepository) {
        this.userRepository = userRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.productRepository = productRepository;
        this.appSettingRepository = appSettingRepository;
        this.loginLogRepository = loginLogRepository;
        this.orderService = orderService;
        this.paymentLogRepository = paymentLogRepository;
    }

    @GetMapping("/overview")
    public AdminOverviewResponse overview(HttpServletRequest request) {
        ensureAdmin(request);
        return new AdminOverviewResponse(
                userRepository.count(),
                userRepository.countByRole("MERCHANT"),
                userRepository.countByRoleAndMerchantStatus("MERCHANT", "PENDING"),
                userRepository.countByRoleAndMerchantStatus("MERCHANT", "APPROVED"),
                customerOrderRepository.count(),
                customerOrderRepository.sumTotalAmount(),
                productRepository.count(),
                productRepository.countByStockLessThanEqual(5)
        );
    }

    @GetMapping("/merchants")
    public List<AdminMerchantResponse> merchants(@RequestParam(required = false) String status, HttpServletRequest request) {
        ensureAdmin(request);
        List<User> merchants;
        if (status != null && !status.isBlank()) {
            merchants = userRepository.findByRoleAndMerchantStatusOrderByIdDesc("MERCHANT", status.toUpperCase(Locale.ROOT));
        } else {
            merchants = userRepository.findByRoleOrderByIdDesc("MERCHANT");
        }
        return merchants.stream()
                .map(u -> new AdminMerchantResponse(u.getId(), u.getUsername(), u.getRole(), u.getMerchantStatus()))
                .toList();
    }

    @PostMapping("/merchants/{id}/status")
    public AdminMerchantResponse updateMerchantStatus(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateMerchantStatusRequest request,
                                                      HttpServletRequest httpRequest) {
        ensureAdmin(httpRequest);
        String status = request.getStatus() == null ? "" : request.getStatus().toUpperCase(Locale.ROOT);
        if (!List.of("APPROVED", "PENDING", "BANNED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "状态不支持");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (!"MERCHANT".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标用户不是商家");
        }
        user.setMerchantStatus(status);
        userRepository.save(user);
        return new AdminMerchantResponse(user.getId(), user.getUsername(), user.getRole(), user.getMerchantStatus());
    }

    @GetMapping("/orders")
    public List<AdminOrderSummary> orders(HttpServletRequest request) {
        ensureAdmin(request);
        return customerOrderRepository.findAllWithItemsOrderByCreatedAtDesc()
                .stream()
                .map(this::toOrderSummary)
                .toList();
    }

    @GetMapping("/orders/pending")
    public List<AdminOrderSummary> pendingApprovals(HttpServletRequest request) {
        ensureAdmin(request);
        return customerOrderRepository.findByStatusOrderByCreatedAtDesc("PENDING_ADMIN")
                .stream()
                .map(this::toOrderSummary)
                .toList();
    }

    @PostMapping("/orders/{id}/approve")
    public void approve(@PathVariable Long id, HttpServletRequest request) {
        ensureAdmin(request);
        orderService.approveOrder(id);
    }

    @PostMapping("/orders/{id}/reject")
    public void reject(@PathVariable Long id, HttpServletRequest request) {
        ensureAdmin(request);
        orderService.rejectOrder(id);
    }

    @PostMapping("/orders/{id}/refund/approve")
    public void approveRefund(@PathVariable Long id, HttpServletRequest request) {
        ensureAdmin(request);
        orderService.approveRefund(id);
    }

    @PostMapping("/orders/{id}/refund/reject")
    public void rejectRefund(@PathVariable Long id, HttpServletRequest request) {
        ensureAdmin(request);
        orderService.rejectRefund(id);
    }

    @GetMapping("/settings")
    public AppSetting getSettings(HttpServletRequest request) {
        ensureAdmin(request);
        return appSettingRepository.findAll().stream().findFirst().orElseGet(() -> appSettingRepository.save(new AppSetting()));
    }

    @PostMapping("/settings/approval")
    public AppSetting setApproval(@RequestBody AppSetting setting, HttpServletRequest request) {
        ensureAdmin(request);
        AppSetting saved = appSettingRepository.findAll().stream().findFirst().orElse(new AppSetting());
        saved.setOrderApprovalLevel(setting.getOrderApprovalLevel() == null ? "LOW" : setting.getOrderApprovalLevel().toUpperCase(Locale.ROOT));
        return appSettingRepository.save(saved);
    }

    @GetMapping("/ai/key")
    public AppSetting getAiKey(HttpServletRequest request) {
        ensureAdmin(request);
        return appSettingRepository.findAll().stream().findFirst().orElseGet(() -> appSettingRepository.save(new AppSetting()));
    }

    @PostMapping("/ai/key")
    public AppSetting saveAiKey(@RequestBody AppSetting setting, HttpServletRequest request) {
        ensureAdmin(request);
        AppSetting saved = appSettingRepository.findAll().stream().findFirst().orElse(new AppSetting());
        saved.setAiApiKey(keep(setting.getAiApiKey(), saved.getAiApiKey()));
        saved.setMapApiKey(keep(setting.getMapApiKey(), saved.getMapApiKey()));
        saved.setMapJsKey(keep(setting.getMapJsKey(), saved.getMapJsKey()));
        saved.setMapJsSec(keep(setting.getMapJsSec(), saved.getMapJsSec()));
        saved.setAlipayAppId(keep(setting.getAlipayAppId(), saved.getAlipayAppId()));
        saved.setAlipayPrivateKey(keep(setting.getAlipayPrivateKey(), saved.getAlipayPrivateKey()));
        saved.setAlipayPublicKey(keep(setting.getAlipayPublicKey(), saved.getAlipayPublicKey()));
        saved.setAlipayGateway(keep(setting.getAlipayGateway(), saved.getAlipayGateway()));
        saved.setAlipayReturnUrl(keep(setting.getAlipayReturnUrl(), saved.getAlipayReturnUrl()));
        saved.setAlipayNotifyUrl(keep(setting.getAlipayNotifyUrl(), saved.getAlipayNotifyUrl()));
        return appSettingRepository.save(saved);
    }

    private String keep(String incoming, String existing) {
        if (incoming != null && !incoming.isBlank()) {
            return incoming;
        }
        return existing;
    }

    @GetMapping("/login-logs")
    public List<LoginLog> loginLogs(HttpServletRequest request) {
        ensureAdmin(request);
        return loginLogRepository.findAll();
    }

    @GetMapping("/system-logs")
    public String systemLogs(@RequestParam(defaultValue = "200") int lines, HttpServletRequest request) {
        ensureAdmin(request);
        Path logPath = Path.of("logs", "app.log");
        if (!Files.exists(logPath)) {
            return "日志文件不存在：" + logPath.toAbsolutePath();
        }
        try {
            List<String> all = Files.readAllLines(logPath);
            int from = Math.max(0, all.size() - lines);
            return String.join("\n", all.subList(from, all.size()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取日志失败");
        }
    }

    @GetMapping("/revenue")
    public List<java.util.Map<String, Object>> revenue(HttpServletRequest request) {
        ensureAdmin(request);
        return paymentLogRepository.findAdminIncome().stream().map(log -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", log.getId());
            m.put("orderNumber", log.getOrderNumber());
            m.put("amount", log.getAmount());
            m.put("type", log.getType());
            m.put("remark", log.getRemark());
            m.put("createdAt", log.getCreatedAt());
            return m;
        }).toList();
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
                order.getItems() == null ? 0 : order.getItems().size());
    }

    private User ensureAdmin(HttpServletRequest request) {
        User user = (User) request.getAttribute("CURRENT_USER");
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
        return user;
    }
}
