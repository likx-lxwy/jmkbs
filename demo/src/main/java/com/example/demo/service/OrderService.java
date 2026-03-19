package com.example.demo.service;

import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.dto.OrderResponse;
import com.example.demo.model.AppSetting;
import com.example.demo.model.CustomerOrder;
import com.example.demo.model.OrderItem;
import com.example.demo.model.PaymentLog;
import com.example.demo.model.Product;
import com.example.demo.model.ProductSize;
import com.example.demo.model.User;
import com.example.demo.model.UserAddress;
import com.example.demo.repository.AppSettingRepository;
import com.example.demo.repository.CustomerOrderRepository;
import com.example.demo.repository.PaymentLogRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.UserAddressRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.math.RoundingMode;

@Service
public class OrderService {

    private final CustomerOrderRepository customerOrderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AppSettingRepository appSettingRepository;
    private final PaymentLogRepository paymentLogRepository;
    private final UserAddressRepository userAddressRepository;
    private final Random random = new Random();
    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.05");
    private static final String PAY_WALLET = "WALLET";
    private static final String PAY_ALIPAY = "ALIPAY";

    public OrderService(CustomerOrderRepository customerOrderRepository,
                        ProductRepository productRepository,
                        UserRepository userRepository,
                        AppSettingRepository appSettingRepository,
                        PaymentLogRepository paymentLogRepository,
                        UserAddressRepository userAddressRepository) {
        this.customerOrderRepository = customerOrderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.appSettingRepository = appSettingRepository;
        this.paymentLogRepository = paymentLogRepository;
        this.userAddressRepository = userAddressRepository;
    }

    @Transactional
    public CustomerOrder createOrder(CreateOrderRequest request, User buyer) {
        if (buyer == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单商品不能为空");
        }

        if (request.getAddressId() != null) {
            UserAddress ua = userAddressRepository.findByIdAndUserId(request.getAddressId(), buyer.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "地址不存在或不属于当前用户"));
            request.setCustomerName(ua.getRecipientName());
            request.setPhone(ua.getPhone());
            request.setAddress(ua.getAddress());
        }

        String payMethod = request.getPayMethod() == null ? PAY_WALLET : request.getPayMethod().toUpperCase();
        if (!payMethod.equals(PAY_WALLET) && !payMethod.equals(PAY_ALIPAY)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的支付方式");
        }

        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber(generateOrderNumber());
        order.setCustomerName(request.getCustomerName());
        order.setPhone(request.getPhone());
        order.setAddress(request.getAddress());
        order.setCreatedAt(LocalDateTime.now());
        order.setBuyer(buyer);
        order.setPayMethod(payMethod);

        BigDecimal total = BigDecimal.ZERO;

        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在: " + itemRequest.getProductId()));

            int quantity = itemRequest.getQuantity() == null ? 0 : itemRequest.getQuantity();
            if (quantity <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "购买数量必须大于0");
            }

            ProductSize matchedSize = null;
            List<ProductSize> sizesDetail = product.getSizesDetail();
            boolean hasSizeDetail = sizesDetail != null && !sizesDetail.isEmpty();
            if (hasSizeDetail) {
                String sizeLabel = itemRequest.getSizeLabel() == null ? "" : itemRequest.getSizeLabel().trim();
                if (sizeLabel.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择尺码: " + product.getName());
                }
                matchedSize = sizesDetail.stream()
                        .filter(s -> s.getLabel() != null && s.getLabel().equalsIgnoreCase(sizeLabel))
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "尺码不存在: " + sizeLabel));
                if (matchedSize.getStock() < quantity) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "尺码库存不足: " + sizeLabel);
                }
                matchedSize.setStock(matchedSize.getStock() - quantity);
                product.setStock(sumStock(sizesDetail));
            } else {
                if (product.getStock() < quantity) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "库存不足: " + product.getName());
                }
                product.setStock(product.getStock() - quantity);
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(quantity);
            orderItem.setUnitPrice(product.getPrice());
            if (matchedSize != null) {
                orderItem.setSizeLabel(matchedSize.getLabel());
            } else if (itemRequest.getSizeLabel() != null) {
                orderItem.setSizeLabel(itemRequest.getSizeLabel());
            }

            order.getItems().add(orderItem);

            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }

        String level = getApprovalLevel();
        boolean needApproval = "HIGH".equalsIgnoreCase(level);
        order.setRequiresApproval(needApproval);
        order.setTotalAmount(total);

        if (payMethod.equals(PAY_WALLET)) {
            // 钱包扣款
            if (buyer.getWalletBalance().compareTo(total) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "余额不足，请充值");
            }
            buyer.setWalletBalance(buyer.getWalletBalance().subtract(total));
            userRepository.save(buyer);
            recordPayment(buyer, order.getOrderNumber(), total, "PAY", "钱包支付");

            if (needApproval) {
                order.setStatus("PENDING_ADMIN");
                order.setEscrowAmount(total);
            } else {
                order.setStatus("PLACED");
                order.setEscrowAmount(BigDecimal.ZERO);
                creditMerchants(order);
            }
        } else {
            // 支付宝：待支付
            order.setStatus("PENDING_PAYMENT");
            order.setEscrowAmount(total);
        }

        return customerOrderRepository.save(order);
    }

    @Transactional
    public void approveOrder(Long id) {
        CustomerOrder order = customerOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.isRequiresApproval() || !"PENDING_ADMIN".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单无需审批");
        }
        creditMerchants(order);
        order.setEscrowAmount(BigDecimal.ZERO);
        order.setStatus("APPROVED");
        customerOrderRepository.save(order);
    }

    @Transactional
    public void markOrderPaid(String orderNumber) {
        CustomerOrder order = customerOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if ("PLACED".equalsIgnoreCase(order.getStatus()) || "APPROVED".equalsIgnoreCase(order.getStatus()) || "PENDING_ADMIN".equalsIgnoreCase(order.getStatus())) {
            // 已处理过支付
            return;
        }
        User buyer = order.getBuyer();
        if (buyer != null) {
            recordPayment(buyer, orderNumber, order.getTotalAmount(), "PAY", "支付宝支付");
        }
        if (order.isRequiresApproval()) {
            order.setStatus("PENDING_ADMIN");
            order.setEscrowAmount(order.getTotalAmount());
        } else {
            order.setStatus("PLACED");
            order.setEscrowAmount(BigDecimal.ZERO);
            creditMerchants(order);
        }
        customerOrderRepository.save(order);
    }

    @Transactional
    public void rejectOrder(Long id) {
        CustomerOrder order = customerOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.isRequiresApproval() || !"PENDING_ADMIN".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单无需审批");
        }
        // 退款
        User buyer = order.getBuyer();
        if (buyer != null) {
            buyer.setWalletBalance(buyer.getWalletBalance().add(order.getEscrowAmount()));
            userRepository.save(buyer);
            recordPayment(buyer, order.getOrderNumber(), order.getEscrowAmount(), "REFUND", "管理员拒绝订单退款");
        }
        order.setEscrowAmount(BigDecimal.ZERO);
        order.setStatus("REJECTED");
        customerOrderRepository.save(order);
    }

    public OrderResponse toResponse(CustomerOrder order) {
        List<OrderResponse.OrderLine> lines = order.getItems()
                .stream()
                .map(item -> new OrderResponse.OrderLine(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSizeLabel()))
                .collect(Collectors.toList());

        return new OrderResponse(order.getId(), order.getOrderNumber(), order.getStatus(), order.getTotalAmount(), lines, order.getCreatedAt(), order.getPayMethod());
    }

    public List<CustomerOrder> listMyOrders(User buyer) {
        if (buyer == null) {
            return List.of();
        }
        // 商家查看“订单”时仅显示与自己商品相关的订单
        if ("MERCHANT".equalsIgnoreCase(buyer.getRole())) {
            return customerOrderRepository.findByProductOwnerIdOrderByCreatedAtDesc(buyer.getId());
        }
        return customerOrderRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.getId());
    }

    @Transactional
    public void refundOrder(Long id, User buyer, String reason) {
        CustomerOrder order = customerOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能退款自己的订单");
        }
        String status = order.getStatus();
        if (!"PLACED".equalsIgnoreCase(status) && !"APPROVED".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单不可退款");
        }
        order.setRefundReason(reason);
        // 已完成的订单需要管理员同意
        if ("APPROVED".equalsIgnoreCase(status)) {
            order.setStatus("REFUND_REQUESTED");
            customerOrderRepository.save(order);
            return;
        }
        if ("PENDING_ADMIN".equalsIgnoreCase(status)) {
            buyer.setWalletBalance(buyer.getWalletBalance().add(order.getEscrowAmount()));
            recordPayment(buyer, order.getOrderNumber(), order.getEscrowAmount(), "REFUND", "待审批订单退款");
            order.setEscrowAmount(BigDecimal.ZERO);
        } else {
            buyer.setWalletBalance(buyer.getWalletBalance().add(order.getTotalAmount()));
            recordPayment(buyer, order.getOrderNumber(), order.getTotalAmount(), "REFUND", "订单退款");
        }
        userRepository.save(buyer);
        order.setStatus("REFUNDED");
        customerOrderRepository.save(order);
    }

    private int sumStock(List<ProductSize> sizes) {
        if (sizes == null) {
            return 0;
        }
        return sizes.stream()
                .map(ProductSize::getStock)
                .filter(v -> v != null)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private void creditMerchants(CustomerOrder order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product.getOwner() != null) {
                User owner = product.getOwner();
                BigDecimal income = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                BigDecimal adminCut = BigDecimal.ZERO;
                if (!isSubscriptionActive(owner)) {
                    adminCut = income.multiply(COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);
                }
                BigDecimal merchantIncome = income.subtract(adminCut);
                owner.setWalletBalance(owner.getWalletBalance().add(merchantIncome));
                userRepository.save(owner);
                recordPayment(owner, order.getOrderNumber(), merchantIncome, "INCOME", "销售收入");
                if (adminCut.compareTo(BigDecimal.ZERO) > 0) {
                    User admin = findAnyAdmin();
                    if (admin != null) {
                        admin.setWalletBalance(admin.getWalletBalance().add(adminCut));
                        userRepository.save(admin);
                        recordPayment(admin, order.getOrderNumber(), adminCut, "INCOME", "商家未缴费抽成");
                    }
                }
            }
        }
    }

    @Transactional
    public void approveRefund(Long id) {
        CustomerOrder order = customerOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!"REFUND_REQUESTED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单未申请退款");
        }
        User buyer = order.getBuyer();
        if (buyer != null) {
            buyer.setWalletBalance(buyer.getWalletBalance().add(order.getTotalAmount()));
            userRepository.save(buyer);
            recordPayment(buyer, order.getOrderNumber(), order.getTotalAmount(), "REFUND", "退款批准");
        }
        order.setStatus("REFUNDED");
        customerOrderRepository.save(order);
    }

    @Transactional
    public void rejectRefund(Long id) {
        CustomerOrder order = customerOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!"REFUND_REQUESTED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单未申请退款");
        }
        order.setStatus("APPROVED");
        customerOrderRepository.save(order);
    }

    private String getApprovalLevel() {
        return appSettingRepository.findAll().stream().findFirst().map(AppSetting::getOrderApprovalLevel).orElse("LOW");
    }

    private String generateOrderNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int suffix = 1000 + random.nextInt(9000);
        return "MK" + timestamp + suffix;
    }

    private void recordPayment(User user, String orderNumber, BigDecimal amount, String type, String remark) {
        if (user == null) {
            return;
        }
        PaymentLog log = new PaymentLog();
        log.setUser(user);
        log.setOrderNumber(orderNumber);
        log.setAmount(amount);
        log.setType(type);
        log.setRemark(remark);
        paymentLogRepository.save(log);
    }

    private boolean isSubscriptionActive(User user) {
        if (user == null || !"MERCHANT".equalsIgnoreCase(user.getRole())) {
            return true;
        }
        LocalDate until = user.getSubscriptionPaidUntil();
        return until != null && !until.isBefore(LocalDate.now());
    }

    private User findAnyAdmin() {
        return userRepository.findFirstByRoleOrderByIdAsc("ADMIN").orElse(null);
    }
}
