package com.example.demo.service;

import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.dto.OrderResponse;
import com.example.demo.mapper.CustomerOrderQueryMapper;
import com.example.demo.mapper.OrderItemQueryMapper;
import com.example.demo.mapper.PaymentLogQueryMapper;
import com.example.demo.mapper.ProductQueryMapper;
import com.example.demo.mapper.ProductSizeQueryMapper;
import com.example.demo.mapper.RefundChatMessageQueryMapper;
import com.example.demo.mapper.UserAddressQueryMapper;
import com.example.demo.mapper.UserQueryMapper;
import com.example.demo.model.CustomerOrder;
import com.example.demo.model.OrderItem;
import com.example.demo.model.PaymentLog;
import com.example.demo.model.Product;
import com.example.demo.model.ProductSize;
import com.example.demo.model.User;
import com.example.demo.model.UserAddress;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.05");
    private static final String PAY_ALIPAY = "ALIPAY";

    private static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String STATUS_PENDING_ADMIN = "PENDING_ADMIN";
    private static final String STATUS_PLACED = "PLACED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REFUND_REQUESTED = "REFUND_REQUESTED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_REFUNDED = "REFUNDED";
    private static final String REFUND_REASON_META_PREFIX = "[[REFUND_ORIGIN=";
    private static final String REFUND_REASON_META_SUFFIX = "]]";
    private static final String REVIEW_META_PREFIX = "[[MERCHANT_REVIEW=";
    private static final String REVIEW_META_SUFFIX = "]]";
    private static final int MAX_REVIEW_CONTENT_LENGTH = 160;
    private static final int MAX_PAYMENT_LOG_REMARK_LENGTH = 200;

    private static final String LOG_TYPE_PAY = "PAY";
    private static final String LOG_TYPE_INCOME = "INCOME";
    private static final String LOG_TYPE_REFUND = "REFUND";
    private static final String LOG_TYPE_ESCROW = "ESCROW";
    private static final String LOG_TYPE_REVIEW = "REVIEW";

    private final CustomerOrderQueryMapper customerOrderQueryMapper;
    private final OrderItemQueryMapper orderItemQueryMapper;
    private final ProductQueryMapper productQueryMapper;
    private final ProductSizeQueryMapper productSizeQueryMapper;
    private final UserQueryMapper userQueryMapper;
    private final PaymentLogQueryMapper paymentLogQueryMapper;
    private final UserAddressQueryMapper userAddressQueryMapper;
    private final RefundChatMessageQueryMapper refundChatMessageQueryMapper;
    private final Random random = new Random();

    public OrderService(CustomerOrderQueryMapper customerOrderQueryMapper,
                        OrderItemQueryMapper orderItemQueryMapper,
                        ProductQueryMapper productQueryMapper,
                        ProductSizeQueryMapper productSizeQueryMapper,
                        UserQueryMapper userQueryMapper,
                        PaymentLogQueryMapper paymentLogQueryMapper,
                        UserAddressQueryMapper userAddressQueryMapper,
                        RefundChatMessageQueryMapper refundChatMessageQueryMapper) {
        this.customerOrderQueryMapper = customerOrderQueryMapper;
        this.orderItemQueryMapper = orderItemQueryMapper;
        this.productQueryMapper = productQueryMapper;
        this.productSizeQueryMapper = productSizeQueryMapper;
        this.userQueryMapper = userQueryMapper;
        this.paymentLogQueryMapper = paymentLogQueryMapper;
        this.userAddressQueryMapper = userAddressQueryMapper;
        this.refundChatMessageQueryMapper = refundChatMessageQueryMapper;
    }

    @Transactional
    public CustomerOrder createOrder(CreateOrderRequest request, User buyer) {
        return createOrderInternal(normalizeCreateOrderRequest(request, buyer), buyer);
    }

    @Transactional
    public List<CustomerOrder> createOrdersByMerchant(CreateOrderRequest request, User buyer) {
        CreateOrderRequest normalizedRequest = normalizeCreateOrderRequest(request, buyer);
        Map<Long, List<CreateOrderRequest.OrderItemRequest>> groupedItems = new LinkedHashMap<>();
        Map<Long, Product> loadedProducts = new LinkedHashMap<>();

        for (CreateOrderRequest.OrderItemRequest itemRequest : normalizedRequest.getItems()) {
            Product product = loadProductForOrder(itemRequest.getProductId(), loadedProducts);
            Long ownerId = product.getOwner().getId();
            groupedItems.computeIfAbsent(ownerId, ignored -> new ArrayList<>())
                    .add(copyOrderItemRequest(itemRequest));
        }

        List<CustomerOrder> orders = new ArrayList<>();
        for (List<CreateOrderRequest.OrderItemRequest> items : groupedItems.values()) {
            orders.add(createOrderInternal(copyCreateOrderRequest(normalizedRequest, items), buyer));
        }
        return orders;
    }

    private CustomerOrder createOrderInternal(CreateOrderRequest request, User buyer) {
        String payMethod = request.getPayMethod();

        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber(generateOrderNumber());
        order.setCustomerName(request.getCustomerName());
        order.setPhone(request.getPhone());
        order.setAddress(request.getAddress());
        order.setCreatedAt(LocalDateTime.now());
        order.setBuyer(buyer);
        order.setPayMethod(payMethod);

        Map<Long, Product> loadedProducts = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getItems()) {
            Product product = loadProductForOrder(itemRequest.getProductId(), loadedProducts);

            int quantity = itemRequest.getQuantity() == null ? 0 : itemRequest.getQuantity();
            if (quantity <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be greater than 0");
            }

            ProductSize matchedSize = null;
            List<ProductSize> sizesDetail = product.getSizesDetail();
            boolean hasSizeDetail = sizesDetail != null && !sizesDetail.isEmpty();
            if (hasSizeDetail) {
                String sizeLabel = itemRequest.getSizeLabel() == null ? "" : itemRequest.getSizeLabel().trim();
                if (sizeLabel.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size is required for product: " + product.getName());
                }
                matchedSize = sizesDetail.stream()
                        .filter(size -> size.getLabel() != null && size.getLabel().equalsIgnoreCase(sizeLabel))
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size not found: " + sizeLabel));
                if (matchedSize.getStock() < quantity) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient stock for size: " + sizeLabel);
                }
                matchedSize.setStock(matchedSize.getStock() - quantity);
                product.setStock(sumStock(sizesDetail));
            } else {
                if (product.getStock() < quantity) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient stock: " + product.getName());
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

        order.setRequiresApproval(false);
        order.setTotalAmount(total);

        order.setStatus(STATUS_PENDING_PAYMENT);
        order.setEscrowAmount(BigDecimal.ZERO);

        persistProductStocks(loadedProducts.values());
        customerOrderQueryMapper.insert(order);
        for (OrderItem item : order.getItems()) {
            item.setOrder(order);
            orderItemQueryMapper.insert(item);
        }
        return order;
    }

    private CreateOrderRequest normalizeCreateOrderRequest(CreateOrderRequest request, User buyer) {
        if (buyer == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order items are required");
        }

        CreateOrderRequest normalized = copyCreateOrderRequest(request, request.getItems().stream()
                .map(this::copyOrderItemRequest)
                .toList());

        if (normalized.getAddressId() != null) {
            UserAddress address = userAddressQueryMapper.findByIdAndUserId(normalized.getAddressId(), buyer.getId());
            if (address == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Address does not belong to current user");
            }
            normalized.setCustomerName(address.getRecipientName());
            normalized.setPhone(address.getPhone());
            normalized.setAddress(address.getAddress());
        }

        normalized.setPayMethod(normalizePayMethod(normalized.getPayMethod()));
        return normalized;
    }

    private CreateOrderRequest copyCreateOrderRequest(CreateOrderRequest source, List<CreateOrderRequest.OrderItemRequest> items) {
        CreateOrderRequest copy = new CreateOrderRequest();
        copy.setCustomerName(source.getCustomerName());
        copy.setPhone(source.getPhone());
        copy.setAddress(source.getAddress());
        copy.setAddressId(source.getAddressId());
        copy.setPayMethod(source.getPayMethod());
        copy.setItems(items);
        return copy;
    }

    private CreateOrderRequest.OrderItemRequest copyOrderItemRequest(CreateOrderRequest.OrderItemRequest source) {
        CreateOrderRequest.OrderItemRequest copy = new CreateOrderRequest.OrderItemRequest();
        copy.setProductId(source.getProductId());
        copy.setQuantity(source.getQuantity());
        copy.setSizeLabel(source.getSizeLabel());
        return copy;
    }

    private String normalizePayMethod(String payMethod) {
        String normalized = payMethod == null ? PAY_ALIPAY : payMethod.trim().toUpperCase();
        if (!PAY_ALIPAY.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only Alipay sandbox payment is supported");
        }
        return normalized;
    }

    @Transactional
    public void confirmReceipt(Long id, User buyer) {
        CustomerOrder order = customerOrderQueryMapper.selectById(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (buyer == null || order.getBuyer() == null || !order.getBuyer().getId().equals(buyer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Can only confirm your own order");
        }
        String status = normalizeStatus(order.getStatus());
        if (!STATUS_PLACED.equals(status) && !STATUS_PENDING_ADMIN.equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not waiting for receipt confirmation");
        }

        settleEscrowToMerchants(order);
        order.setEscrowAmount(BigDecimal.ZERO);
        order.setStatus(STATUS_APPROVED);
        customerOrderQueryMapper.update(order);
    }

    @Transactional
    public void reviewMerchant(Long id, User buyer, Integer rating, String content) {
        CustomerOrder order = customerOrderQueryMapper.selectById(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (buyer == null || order.getBuyer() == null || !order.getBuyer().getId().equals(buyer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Can only review your own order");
        }
        if (!STATUS_APPROVED.equals(normalizeStatus(order.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only review merchants after receipt confirmation");
        }

        ensureSingleMerchantOrder(order, "merchant review");
        if (paymentLogQueryMapper.existsByOrderNumberAndType(order.getOrderNumber(), LOG_TYPE_REVIEW)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Merchant review already submitted");
        }

        int normalizedRating = normalizeReviewRating(rating);
        String normalizedContent = normalizeReviewContent(content);
        recordPayment(buyer, order.getOrderNumber(), BigDecimal.ZERO, LOG_TYPE_REVIEW, encodeReviewRemark(normalizedRating, normalizedContent));
    }

    @Transactional
    public void markOrderPaid(String orderNumber) {
        CustomerOrder order = customerOrderQueryMapper.selectByOrderNumber(orderNumber);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        String status = normalizeStatus(order.getStatus());
        if (!STATUS_PENDING_PAYMENT.equals(status)) {
            if (STATUS_PENDING_ADMIN.equals(status)
                    || STATUS_PLACED.equals(status)
                    || STATUS_APPROVED.equals(status)
                    || STATUS_REFUND_REQUESTED.equals(status)
                    || STATUS_REFUNDED.equals(status)
                    || STATUS_REJECTED.equals(status)) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not pending payment");
        }

        User buyer = order.getBuyer();
        if (buyer != null) {
            recordPayment(buyer, orderNumber, order.getTotalAmount(), LOG_TYPE_PAY, "alipay payment");
        }

        holdEscrow(order, order.getTotalAmount());
        adjustSalesCountForOrder(order, 1L);
        customerOrderQueryMapper.update(order);
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
        MerchantReviewSummary reviewSummary = resolveMerchantReview(order);

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                lines,
                order.getCreatedAt(),
                order.getPayMethod(),
                extractRefundReason(order.getRefundReason()),
                order.getBuyer() == null ? null : order.getBuyer().getUsername(),
                resolveMerchantName(order),
                resolveRefundChatCount(order),
                reviewSummary.getRating(),
                reviewSummary.getContent(),
                reviewSummary.getCreatedAt(),
                reviewSummary.isPresent()
        );
    }

    public List<CustomerOrder> listMyOrders(User buyer) {
        if (buyer == null) {
            return List.of();
        }
        if ("MERCHANT".equalsIgnoreCase(buyer.getRole())) {
            return customerOrderQueryMapper.selectByProductOwnerIdOrderByCreatedAtDesc(buyer.getId());
        }
        return customerOrderQueryMapper.selectByBuyerIdOrderByCreatedAtDesc(buyer.getId());
    }

    @Transactional
    public void refundOrder(Long id, User buyer, String reason) {
        CustomerOrder order = customerOrderQueryMapper.selectById(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (buyer == null || order.getBuyer() == null || !order.getBuyer().getId().equals(buyer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Can only refund your own order");
        }

        String status = normalizeStatus(order.getStatus());
        if (!STATUS_PENDING_ADMIN.equals(status)
                && !STATUS_PLACED.equals(status)
                && !STATUS_APPROVED.equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order cannot be refunded");
        }

        ensureSingleMerchantOrder(order, "merchant refund review");
        order.setRefundReason(encodeRefundReason(status, reason));
        order.setStatus(STATUS_REFUND_REQUESTED);
        customerOrderQueryMapper.update(order);
    }

    @Transactional
    public void approveRefund(Long id, User merchant) {
        CustomerOrder order = customerOrderQueryMapper.selectById(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (!STATUS_REFUND_REQUESTED.equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order has no pending refund request");
        }
        ensureMerchantCanReviewRefund(order, merchant);

        String originalStatus = resolveOriginalRefundStatus(order.getRefundReason());
        if (STATUS_PENDING_ADMIN.equals(originalStatus) || STATUS_PLACED.equals(originalStatus)) {
            refundEscrowToBuyer(order, resolveEscrowAmount(order), "merchant approved refund");
            adjustSalesCountForOrder(order, -1L);
            order.setEscrowAmount(BigDecimal.ZERO);
            order.setRefundReason(extractRefundReason(order.getRefundReason()));
            order.setStatus(STATUS_REFUNDED);
            customerOrderQueryMapper.update(order);
            return;
        }

        RefundBreakdown refundBreakdown = buildRefundBreakdown(order.getOrderNumber());
        if (refundBreakdown.isEmpty()) {
            SettlementSummary summary = buildSettlementSummary(order);
            for (Map.Entry<Long, BigDecimal> entry : summary.getMerchantIncomeByOwner().entrySet()) {
                refundBreakdown.addMerchantRefund(entry.getKey(), entry.getValue());
            }
            refundBreakdown.addCommission(summary.getCommissionAmount());
        }
        for (Map.Entry<Long, BigDecimal> entry : refundBreakdown.getMerchantRefunds().entrySet()) {
            User merchantUser = userQueryMapper.selectById(entry.getKey());
            if (merchantUser == null) {
                continue;
            }
            BigDecimal amount = entry.getValue();
            merchantUser.setWalletBalance(merchantUser.getWalletBalance().subtract(amount));
            userQueryMapper.update(merchantUser);
            recordPayment(merchantUser, order.getOrderNumber(), amount.negate(), LOG_TYPE_REFUND, "order refund deduction");
        }

        if (refundBreakdown.getCommissionAmount().compareTo(BigDecimal.ZERO) > 0) {
            User admin = findRequiredAdmin();
            ensureSufficientBalance(admin, refundBreakdown.getCommissionAmount(), "Admin wallet balance is insufficient");
            admin.setWalletBalance(admin.getWalletBalance().subtract(refundBreakdown.getCommissionAmount()));
            userQueryMapper.update(admin);
            recordPayment(admin, order.getOrderNumber(), refundBreakdown.getCommissionAmount().negate(), LOG_TYPE_REFUND, "order commission reversal");
        }

        User buyer = order.getBuyer();
        if (buyer != null) {
            buyer.setWalletBalance(buyer.getWalletBalance().add(order.getTotalAmount()));
            userQueryMapper.update(buyer);
            recordPayment(buyer, order.getOrderNumber(), order.getTotalAmount(), LOG_TYPE_REFUND, "merchant approved refund");
        }

        adjustSalesCountForOrder(order, -1L);
        order.setEscrowAmount(BigDecimal.ZERO);
        order.setRefundReason(extractRefundReason(order.getRefundReason()));
        order.setStatus(STATUS_REFUNDED);
        customerOrderQueryMapper.update(order);
    }

    @Transactional
    public void rejectRefund(Long id, User merchant) {
        CustomerOrder order = customerOrderQueryMapper.selectById(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (!STATUS_REFUND_REQUESTED.equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order has no pending refund request");
        }
        ensureMerchantCanReviewRefund(order, merchant);
        String originalStatus = resolveOriginalRefundStatus(order.getRefundReason());
        order.setRefundReason(null);
        order.setStatus(originalStatus);
        customerOrderQueryMapper.update(order);
    }

    private void ensureMerchantCanReviewRefund(CustomerOrder order, User merchant) {
        if (merchant == null || !"MERCHANT".equalsIgnoreCase(merchant.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant permission required");
        }
        Long ownerId = ensureSingleMerchantOrder(order, "merchant refund review");
        if (!ownerId.equals(merchant.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Can only review refund requests for your own orders");
        }
    }

    private Long ensureSingleMerchantOrder(CustomerOrder order, String scenario) {
        Set<Long> ownerIds = order.getItems() == null
                ? Set.of()
                : order.getItems().stream()
                .map(OrderItem::getProduct)
                .filter(Objects::nonNull)
                .map(Product::getOwner)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (ownerIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order has no merchant owner");
        }
        if (ownerIds.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mixed-merchant orders are not supported for " + scenario);
        }
        return ownerIds.iterator().next();
    }

    private String encodeRefundReason(String originalStatus, String reason) {
        String normalizedStatus = normalizeStatus(originalStatus);
        String remark = reason == null ? "" : reason.trim();
        String prefix = REFUND_REASON_META_PREFIX + normalizedStatus + REFUND_REASON_META_SUFFIX;
        int remaining = Math.max(0, 255 - prefix.length());
        if (remark.length() > remaining) {
            remark = remark.substring(0, remaining);
        }
        return prefix + remark;
    }

    private String resolveOriginalRefundStatus(String storedReason) {
        if (storedReason != null && storedReason.startsWith(REFUND_REASON_META_PREFIX)) {
            int endIndex = storedReason.indexOf(REFUND_REASON_META_SUFFIX);
            if (endIndex > REFUND_REASON_META_PREFIX.length()) {
                String status = normalizeStatus(storedReason.substring(REFUND_REASON_META_PREFIX.length(), endIndex));
                return STATUS_PENDING_ADMIN.equals(status) ? STATUS_PLACED : status;
            }
        }
        return STATUS_APPROVED;
    }

    private String extractRefundReason(String storedReason) {
        if (storedReason == null || storedReason.isBlank()) {
            return null;
        }
        if (!storedReason.startsWith(REFUND_REASON_META_PREFIX)) {
            return storedReason;
        }
        int endIndex = storedReason.indexOf(REFUND_REASON_META_SUFFIX);
        if (endIndex < 0) {
            return storedReason;
        }
        String reason = storedReason.substring(endIndex + REFUND_REASON_META_SUFFIX.length()).trim();
        return reason.isEmpty() ? null : reason;
    }

    private int normalizeReviewRating(Integer rating) {
        int normalized = rating == null ? 0 : rating;
        if (normalized < 1 || normalized > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be between 1 and 5");
        }
        return normalized;
    }

    private String normalizeReviewContent(String content) {
        String normalized = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review content cannot be empty");
        }
        if (normalized.length() > MAX_REVIEW_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review content cannot exceed 160 characters");
        }
        return normalized;
    }

    private String encodeReviewRemark(int rating, String content) {
        String prefix = REVIEW_META_PREFIX + rating + REVIEW_META_SUFFIX;
        int remaining = Math.max(0, MAX_PAYMENT_LOG_REMARK_LENGTH - prefix.length());
        String trimmedContent = content == null ? "" : content.trim();
        if (trimmedContent.length() > remaining) {
            trimmedContent = trimmedContent.substring(0, remaining);
        }
        return prefix + trimmedContent;
    }

    private MerchantReviewSummary resolveMerchantReview(CustomerOrder order) {
        if (order == null || order.getOrderNumber() == null || order.getOrderNumber().isBlank()) {
            return MerchantReviewSummary.empty();
        }
        try {
            MerchantReviewSummary latest = MerchantReviewSummary.empty();
            for (PaymentLog log : paymentLogQueryMapper.findByOrderNumberOrderByIdAsc(order.getOrderNumber())) {
                if (!LOG_TYPE_REVIEW.equalsIgnoreCase(log.getType())) {
                    continue;
                }
                MerchantReviewSummary parsed = parseMerchantReview(log);
                if (parsed.isPresent()) {
                    latest = parsed;
                }
            }
            return latest;
        } catch (RuntimeException ex) {
            return MerchantReviewSummary.empty();
        }
    }

    private MerchantReviewSummary parseMerchantReview(PaymentLog log) {
        if (log == null || log.getRemark() == null || log.getRemark().isBlank()) {
            return MerchantReviewSummary.empty();
        }
        String remark = log.getRemark().trim();
        if (!remark.startsWith(REVIEW_META_PREFIX)) {
            return MerchantReviewSummary.empty();
        }
        int endIndex = remark.indexOf(REVIEW_META_SUFFIX);
        if (endIndex <= REVIEW_META_PREFIX.length()) {
            return MerchantReviewSummary.empty();
        }
        try {
            int rating = Integer.parseInt(remark.substring(REVIEW_META_PREFIX.length(), endIndex));
            if (rating < 1 || rating > 5) {
                return MerchantReviewSummary.empty();
            }
            String content = remark.substring(endIndex + REVIEW_META_SUFFIX.length()).trim();
            return new MerchantReviewSummary(rating, content.isEmpty() ? null : content, log.getCreatedAt(), true);
        } catch (NumberFormatException ex) {
            return MerchantReviewSummary.empty();
        }
    }

    private Product loadProductForOrder(Long productId, Map<Long, Product> cache) {
        Product cached = cache.get(productId);
        if (cached != null) {
            return cached;
        }
        Product product = productQueryMapper.selectById(productId);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + productId);
        }
        if (product.getOwner() == null || product.getOwner().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product has no merchant owner: " + productId);
        }
        cache.put(productId, product);
        return product;
    }

    private String resolveMerchantName(CustomerOrder order) {
        if (order == null || order.getItems() == null) {
            return null;
        }
        return order.getItems().stream()
                .map(OrderItem::getProduct)
                .filter(Objects::nonNull)
                .map(Product::getOwner)
                .filter(Objects::nonNull)
                .map(User::getUsername)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private long resolveRefundChatCount(CustomerOrder order) {
        if (order == null || order.getId() == null) {
            return 0L;
        }
        try {
            return refundChatMessageQueryMapper.countByOrderId(order.getId());
        } catch (RuntimeException ex) {
            return 0L;
        }
    }

    private void persistProductStocks(Iterable<Product> products) {
        for (Product product : products) {
            productQueryMapper.update(product);
            if (product.getSizesDetail() != null) {
                for (ProductSize size : product.getSizesDetail()) {
                    productSizeQueryMapper.update(size);
                }
            }
        }
    }

    private int sumStock(List<ProductSize> sizes) {
        if (sizes == null) {
            return 0;
        }
        return sizes.stream()
                .map(ProductSize::getStock)
                .filter(stock -> stock != null)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private void adjustSalesCountForOrder(CustomerOrder order, long direction) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty() || direction == 0L) {
            return;
        }
        for (OrderItem item : order.getItems()) {
            if (item == null || item.getProduct() == null || item.getProduct().getId() == null) {
                continue;
            }
            long quantity = item.getQuantity() == null ? 0L : item.getQuantity();
            if (quantity <= 0) {
                continue;
            }
            productQueryMapper.updateSalesCountDelta(item.getProduct().getId(), quantity * direction);
        }
    }

    private void holdEscrow(CustomerOrder order, BigDecimal amount) {
        User admin = findRequiredAdmin();
        admin.setWalletBalance(admin.getWalletBalance().add(amount));
        userQueryMapper.update(admin);
        recordPayment(admin, order.getOrderNumber(), amount, LOG_TYPE_ESCROW, "order escrow hold");
        order.setEscrowAmount(amount);
        order.setStatus(STATUS_PLACED);
    }

    private void settleEscrowToMerchants(CustomerOrder order) {
        SettlementSummary summary = buildSettlementSummary(order);
        BigDecimal grossAmount = summary.getMerchantAmount().add(summary.getCommissionAmount());
        User admin = findRequiredAdmin();

        if (grossAmount.compareTo(BigDecimal.ZERO) > 0) {
            ensureSufficientBalance(admin, grossAmount, "Admin escrow balance is insufficient");
            admin.setWalletBalance(admin.getWalletBalance().subtract(grossAmount));
            userQueryMapper.update(admin);
            recordPayment(admin, order.getOrderNumber(), grossAmount.negate(), LOG_TYPE_ESCROW, "order escrow settlement");
        }

        if (summary.getCommissionAmount().compareTo(BigDecimal.ZERO) > 0) {
            admin.setWalletBalance(admin.getWalletBalance().add(summary.getCommissionAmount()));
            userQueryMapper.update(admin);
            recordPayment(admin, order.getOrderNumber(), summary.getCommissionAmount(), LOG_TYPE_INCOME, "order commission");
        }

        for (Map.Entry<Long, BigDecimal> entry : summary.getMerchantIncomeByOwner().entrySet()) {
            User merchant = userQueryMapper.selectById(entry.getKey());
            if (merchant == null) {
                continue;
            }
            BigDecimal income = entry.getValue();
            merchant.setWalletBalance(merchant.getWalletBalance().add(income));
            userQueryMapper.update(merchant);
            recordPayment(merchant, order.getOrderNumber(), income, LOG_TYPE_INCOME, "product income");
        }
    }

    private SettlementSummary buildSettlementSummary(CustomerOrder order) {
        SettlementSummary summary = new SettlementSummary();
        if (order.getItems() == null) {
            return summary;
        }

        Map<Long, User> ownerCache = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product == null || product.getOwner() == null || product.getOwner().getId() == null) {
                continue;
            }

            Long ownerId = product.getOwner().getId();
            User owner = ownerCache.computeIfAbsent(ownerId, userQueryMapper::selectById);
            if (owner == null) {
                continue;
            }

            BigDecimal gross = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal commission = gross.multiply(COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal merchantIncome = gross.subtract(commission);
            summary.add(ownerId, merchantIncome, commission);
        }
        return summary;
    }

    private RefundBreakdown buildRefundBreakdown(String orderNumber) {
        RefundBreakdown breakdown = new RefundBreakdown();
        List<PaymentLog> logs = paymentLogQueryMapper.findByOrderNumberOrderByIdAsc(orderNumber);
        for (PaymentLog log : logs) {
            if (log.getUser() == null || log.getAmount() == null) {
                continue;
            }

            User user = log.getUser();
            String type = normalizeStatus(log.getType());
            String remark = log.getRemark() == null ? "" : log.getRemark().trim().toLowerCase();

            if (LOG_TYPE_INCOME.equals(type)
                    && "MERCHANT".equalsIgnoreCase(user.getRole())
                    && "product income".equals(remark)) {
                breakdown.addMerchantRefund(user.getId(), log.getAmount());
            }

            if (LOG_TYPE_INCOME.equals(type)
                    && "ADMIN".equalsIgnoreCase(user.getRole())
                    && "order commission".equals(remark)) {
                breakdown.addCommission(log.getAmount());
            }
        }
        return breakdown;
    }

    private void refundEscrowToBuyer(CustomerOrder order, BigDecimal amount, String buyerRemark) {
        BigDecimal refundAmount = amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                ? order.getTotalAmount()
                : amount;

        User admin = findRequiredAdmin();
        ensureSufficientBalance(admin, refundAmount, "Admin escrow balance is insufficient");
        admin.setWalletBalance(admin.getWalletBalance().subtract(refundAmount));
        userQueryMapper.update(admin);
        recordPayment(admin, order.getOrderNumber(), refundAmount.negate(), LOG_TYPE_ESCROW, "order refund payout");

        User buyer = order.getBuyer();
        if (buyer != null) {
            buyer.setWalletBalance(buyer.getWalletBalance().add(refundAmount));
            userQueryMapper.update(buyer);
            recordPayment(buyer, order.getOrderNumber(), refundAmount, LOG_TYPE_REFUND, buyerRemark);
        }
    }

    private BigDecimal resolveEscrowAmount(CustomerOrder order) {
        if (order.getEscrowAmount() != null && order.getEscrowAmount().compareTo(BigDecimal.ZERO) > 0) {
            return order.getEscrowAmount();
        }
        return order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
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
        paymentLogQueryMapper.insert(log);
    }

    private void ensureSufficientBalance(User user, BigDecimal amount, String message) {
        if (user == null || user.getWalletBalance() == null || amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        if (user.getWalletBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private User findRequiredAdmin() {
        User admin = userQueryMapper.selectFirstByRoleOrderByIdAsc("ADMIN");
        if (admin == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Admin account not found");
        }
        return admin;
    }

    private static class SettlementSummary {
        private final Map<Long, BigDecimal> merchantIncomeByOwner = new LinkedHashMap<>();
        private BigDecimal merchantAmount = BigDecimal.ZERO;
        private BigDecimal commissionAmount = BigDecimal.ZERO;

        void add(Long ownerId, BigDecimal merchantIncome, BigDecimal commission) {
            merchantIncomeByOwner.merge(ownerId, merchantIncome, BigDecimal::add);
            merchantAmount = merchantAmount.add(merchantIncome);
            commissionAmount = commissionAmount.add(commission);
        }

        Map<Long, BigDecimal> getMerchantIncomeByOwner() {
            return merchantIncomeByOwner;
        }

        BigDecimal getMerchantAmount() {
            return merchantAmount;
        }

        BigDecimal getCommissionAmount() {
            return commissionAmount;
        }
    }

    private static class RefundBreakdown {
        private final Map<Long, BigDecimal> merchantRefunds = new LinkedHashMap<>();
        private BigDecimal commissionAmount = BigDecimal.ZERO;

        void addMerchantRefund(Long merchantId, BigDecimal amount) {
            merchantRefunds.merge(merchantId, amount, BigDecimal::add);
        }

        void addCommission(BigDecimal amount) {
            commissionAmount = commissionAmount.add(amount);
        }

        Map<Long, BigDecimal> getMerchantRefunds() {
            return merchantRefunds;
        }

        BigDecimal getCommissionAmount() {
            return commissionAmount;
        }

        boolean isEmpty() {
            return merchantRefunds.isEmpty() && commissionAmount.compareTo(BigDecimal.ZERO) == 0;
        }
    }

    private static class MerchantReviewSummary {
        private final Integer rating;
        private final String content;
        private final LocalDateTime createdAt;
        private final boolean present;

        private MerchantReviewSummary(Integer rating, String content, LocalDateTime createdAt, boolean present) {
            this.rating = rating;
            this.content = content;
            this.createdAt = createdAt;
            this.present = present;
        }

        static MerchantReviewSummary empty() {
            return new MerchantReviewSummary(null, null, null, false);
        }

        Integer getRating() {
            return rating;
        }

        String getContent() {
            return content;
        }

        LocalDateTime getCreatedAt() {
            return createdAt;
        }

        boolean isPresent() {
            return present;
        }
    }
}
