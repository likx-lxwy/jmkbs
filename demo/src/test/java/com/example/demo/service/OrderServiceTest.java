package com.example.demo.service;

import com.example.demo.dto.CreateOrderRequest;
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
import com.example.demo.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private CustomerOrderQueryMapper customerOrderQueryMapper;

    @Mock
    private OrderItemQueryMapper orderItemQueryMapper;

    @Mock
    private ProductQueryMapper productQueryMapper;

    @Mock
    private ProductSizeQueryMapper productSizeQueryMapper;

    @Mock
    private UserQueryMapper userQueryMapper;

    @Mock
    private PaymentLogQueryMapper paymentLogQueryMapper;

    @Mock
    private UserAddressQueryMapper userAddressQueryMapper;

    @Mock
    private RefundChatMessageQueryMapper refundChatMessageQueryMapper;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrderWithAlipayStartsPendingPayment() {
        User buyer = user(1L, "buyer", "USER", "200.00");
        User merchant = user(2L, "merchant", "MERCHANT", "10.00");
        Product product = product(11L, merchant, "100.00", 5);

        CreateOrderRequest request = createRequest("ALIPAY", 11L, 2);

        when(productQueryMapper.selectById(11L)).thenReturn(product);
        doAnswer(invocation -> {
            CustomerOrder order = invocation.getArgument(0);
            order.setId(99L);
            return 1;
        }).when(customerOrderQueryMapper).insert(any(CustomerOrder.class));

        CustomerOrder order = orderService.createOrder(request, buyer);

        assertThat(order.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.getEscrowAmount()).isEqualByComparingTo("0.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("200.00");
        assertThat(order.getPayMethod()).isEqualTo("ALIPAY");
        assertThat(buyer.getWalletBalance()).isEqualByComparingTo("200.00");
        assertThat(product.getStock()).isEqualTo(3);

        verify(userQueryMapper, times(0)).update(any(User.class));
        verify(productQueryMapper).update(product);
        verify(orderItemQueryMapper).insert(any(OrderItem.class));
        verifyNoInteractions(paymentLogQueryMapper);
    }

    @Test
    void createOrderRejectsWalletPayment() {
        User buyer = user(1L, "buyer", "USER", "200.00");
        CreateOrderRequest request = createRequest("WALLET", 11L, 1);

        assertThatThrownBy(() -> orderService.createOrder(request, buyer))
                .hasMessageContaining("Only Alipay sandbox payment is supported");

        verifyNoInteractions(productQueryMapper, customerOrderQueryMapper, orderItemQueryMapper, paymentLogQueryMapper);
    }

    @Test
    void markOrderPaidMovesAlipayFundsIntoAdminEscrow() {
        User buyer = user(1L, "buyer", "USER", "50.00");
        User admin = user(3L, "admin", "ADMIN", "20.00");
        CustomerOrder order = new CustomerOrder();
        order.setId(101L);
        order.setOrderNumber("ORD-ALIPAY");
        order.setBuyer(buyer);
        order.setPayMethod("ALIPAY");
        order.setStatus("PENDING_PAYMENT");
        order.setRequiresApproval(false);
        order.setTotalAmount(new BigDecimal("120.00"));
        order.setEscrowAmount(BigDecimal.ZERO);

        when(customerOrderQueryMapper.selectByOrderNumber("ORD-ALIPAY")).thenReturn(order);
        when(userQueryMapper.selectFirstByRoleOrderByIdAsc("ADMIN")).thenReturn(admin);

        orderService.markOrderPaid("ORD-ALIPAY");

        assertThat(order.getStatus()).isEqualTo("PLACED");
        assertThat(order.getEscrowAmount()).isEqualByComparingTo("120.00");
        assertThat(admin.getWalletBalance()).isEqualByComparingTo("140.00");
        assertThat(buyer.getWalletBalance()).isEqualByComparingTo("50.00");

        verify(customerOrderQueryMapper).update(order);
        List<PaymentLog> logs = captureInsertedLogs(2);
        assertThat(logs)
                .extracting(PaymentLog::getType, PaymentLog::getRemark, log -> log.getUser().getId(), PaymentLog::getAmount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("PAY", "alipay payment", 1L, new BigDecimal("120.00")),
                        org.assertj.core.groups.Tuple.tuple("ESCROW", "order escrow hold", 3L, new BigDecimal("120.00"))
                );
    }

    @Test
    void confirmReceiptSettlesEscrowAndKeepsCommission() {
        User buyer = user(1L, "buyer", "USER", "0.00");
        User merchant = user(2L, "merchant", "MERCHANT", "10.00");
        User admin = user(3L, "admin", "ADMIN", "200.00");

        CustomerOrder order = new CustomerOrder();
        order.setId(102L);
        order.setOrderNumber("ORD-RECEIPT");
        order.setBuyer(buyer);
        order.setStatus("PLACED");
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setEscrowAmount(new BigDecimal("100.00"));
        order.setItems(List.of(orderItem(product(11L, merchant, "100.00", 8), 1, "100.00")));

        when(customerOrderQueryMapper.selectById(102L)).thenReturn(order);
        when(userQueryMapper.selectFirstByRoleOrderByIdAsc("ADMIN")).thenReturn(admin);
        when(userQueryMapper.selectById(2L)).thenReturn(merchant);

        orderService.confirmReceipt(102L, buyer);

        assertThat(order.getStatus()).isEqualTo("APPROVED");
        assertThat(order.getEscrowAmount()).isEqualByComparingTo("0.00");
        assertThat(admin.getWalletBalance()).isEqualByComparingTo("105.00");
        assertThat(merchant.getWalletBalance()).isEqualByComparingTo("105.00");

        verify(customerOrderQueryMapper).update(order);
        List<PaymentLog> logs = captureInsertedLogs(3);
        assertThat(logs)
                .extracting(PaymentLog::getType, PaymentLog::getRemark, log -> log.getUser().getId(), PaymentLog::getAmount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ESCROW", "order escrow settlement", 3L, new BigDecimal("-100.00")),
                        org.assertj.core.groups.Tuple.tuple("INCOME", "order commission", 3L, new BigDecimal("5.00")),
                        org.assertj.core.groups.Tuple.tuple("INCOME", "product income", 2L, new BigDecimal("95.00"))
                );
    }

    @Test
    void confirmReceiptStillChargesCommissionWhenLegacySubscriptionDateExists() {
        User buyer = user(1L, "buyer", "USER", "0.00");
        User merchant = user(2L, "merchant", "MERCHANT", "0.00");
        merchant.setSubscriptionPaidUntil(LocalDate.now().plusDays(30));
        User admin = user(3L, "admin", "ADMIN", "200.00");

        CustomerOrder order = new CustomerOrder();
        order.setId(107L);
        order.setOrderNumber("ORD-LEGACY-SUB");
        order.setBuyer(buyer);
        order.setStatus("PLACED");
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setEscrowAmount(new BigDecimal("100.00"));
        order.setItems(List.of(orderItem(product(11L, merchant, "100.00", 8), 1, "100.00")));

        when(customerOrderQueryMapper.selectById(107L)).thenReturn(order);
        when(userQueryMapper.selectFirstByRoleOrderByIdAsc("ADMIN")).thenReturn(admin);
        when(userQueryMapper.selectById(2L)).thenReturn(merchant);

        orderService.confirmReceipt(107L, buyer);

        assertThat(admin.getWalletBalance()).isEqualByComparingTo("105.00");
        assertThat(merchant.getWalletBalance()).isEqualByComparingTo("95.00");
    }

    @Test
    void createOrdersByMerchantSplitsCartIntoSeparateOrders() {
        User buyer = user(1L, "buyer", "USER", "300.00");
        User merchantA = user(2L, "merchantA", "MERCHANT", "10.00");
        User merchantB = user(4L, "merchantB", "MERCHANT", "20.00");
        Product productA = product(11L, merchantA, "100.00", 5);
        Product productB = product(12L, merchantB, "50.00", 6);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("Buyer");
        request.setPhone("13800000000");
        request.setAddress("Shanghai");
        request.setPayMethod("ALIPAY");
        request.setItems(List.of(
                orderItemRequest(11L, 2, null),
                orderItemRequest(12L, 2, null)
        ));

        when(productQueryMapper.selectById(11L)).thenReturn(productA);
        when(productQueryMapper.selectById(12L)).thenReturn(productB);
        AtomicLong nextId = new AtomicLong(200L);
        doAnswer(invocation -> {
            CustomerOrder order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(nextId.getAndIncrement());
            }
            return 1;
        }).when(customerOrderQueryMapper).insert(any(CustomerOrder.class));

        List<CustomerOrder> orders = orderService.createOrdersByMerchant(request, buyer);

        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(CustomerOrder::getTotalAmount)
                .containsExactly(new BigDecimal("200.00"), new BigDecimal("100.00"));
        assertThat(orders).extracting(CustomerOrder::getStatus)
                .containsExactly("PENDING_PAYMENT", "PENDING_PAYMENT");
        assertThat(buyer.getWalletBalance()).isEqualByComparingTo("300.00");
        assertThat(productA.getStock()).isEqualTo(3);
        assertThat(productB.getStock()).isEqualTo(4);

        verify(customerOrderQueryMapper, times(2)).insert(any(CustomerOrder.class));
        verify(orderItemQueryMapper, times(2)).insert(any(OrderItem.class));
        verifyNoInteractions(paymentLogQueryMapper);
    }

    @Test
    void refundOrderCreatesMerchantReviewRequest() {
        User buyer = user(1L, "buyer", "USER", "0.00");
        User merchant = user(2L, "merchant", "MERCHANT", "95.00");

        CustomerOrder order = new CustomerOrder();
        order.setId(103L);
        order.setOrderNumber("ORD-REFUND-REQUEST");
        order.setBuyer(buyer);
        order.setStatus("PLACED");
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setEscrowAmount(new BigDecimal("100.00"));
        order.setItems(List.of(orderItem(product(11L, merchant, "100.00", 8), 1, "100.00")));

        when(customerOrderQueryMapper.selectById(103L)).thenReturn(order);

        orderService.refundOrder(103L, buyer, "size mismatch");

        assertThat(order.getStatus()).isEqualTo("REFUND_REQUESTED");
        assertThat(order.getEscrowAmount()).isEqualByComparingTo("100.00");
        assertThat(orderService.toResponse(order).getRefundReason()).isEqualTo("size mismatch");

        verify(customerOrderQueryMapper).update(order);
        verifyNoInteractions(paymentLogQueryMapper);
    }

    @Test
    void approveRefundForEscrowOrderReturnsMoneyFromAdmin() {
        User buyer = user(1L, "buyer", "USER", "0.00");
        User merchant = user(2L, "merchant", "MERCHANT", "10.00");
        User admin = user(3L, "admin", "ADMIN", "100.00");

        CustomerOrder order = new CustomerOrder();
        order.setId(104L);
        order.setOrderNumber("ORD-ESCROW-REFUND");
        order.setBuyer(buyer);
        order.setStatus("REFUND_REQUESTED");
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setEscrowAmount(new BigDecimal("100.00"));
        order.setRefundReason(refundReason("PLACED", "changed mind"));
        order.setItems(List.of(orderItem(product(11L, merchant, "100.00", 8), 1, "100.00")));

        when(customerOrderQueryMapper.selectById(104L)).thenReturn(order);
        when(userQueryMapper.selectFirstByRoleOrderByIdAsc("ADMIN")).thenReturn(admin);

        orderService.approveRefund(104L, merchant);

        assertThat(order.getStatus()).isEqualTo("REFUNDED");
        assertThat(order.getEscrowAmount()).isEqualByComparingTo("0.00");
        assertThat(admin.getWalletBalance()).isEqualByComparingTo("0.00");
        assertThat(merchant.getWalletBalance()).isEqualByComparingTo("10.00");
        assertThat(buyer.getWalletBalance()).isEqualByComparingTo("100.00");
        assertThat(order.getRefundReason()).isEqualTo("changed mind");

        verify(customerOrderQueryMapper).update(order);
        List<PaymentLog> logs = captureInsertedLogs(2);
        assertThat(logs)
                .extracting(PaymentLog::getType, PaymentLog::getRemark, log -> log.getUser().getId(), PaymentLog::getAmount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ESCROW", "order refund payout", 3L, new BigDecimal("-100.00")),
                        org.assertj.core.groups.Tuple.tuple("REFUND", "merchant approved refund", 1L, new BigDecimal("100.00"))
                );
    }

    @Test
    void approveRefundRollsBackMerchantIncomeAndCommission() {
        User buyer = user(1L, "buyer", "USER", "0.00");
        User merchant = user(2L, "merchant", "MERCHANT", "95.00");
        User admin = user(3L, "admin", "ADMIN", "5.00");

        CustomerOrder order = new CustomerOrder();
        order.setId(105L);
        order.setOrderNumber("ORD-REFUND");
        order.setBuyer(buyer);
        order.setStatus("REFUND_REQUESTED");
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundReason(refundReason("APPROVED", "quality issue"));
        order.setItems(List.of(orderItem(product(11L, merchant, "100.00", 8), 1, "100.00")));

        when(customerOrderQueryMapper.selectById(105L)).thenReturn(order);
        when(userQueryMapper.selectFirstByRoleOrderByIdAsc("ADMIN")).thenReturn(admin);
        when(userQueryMapper.selectById(2L)).thenReturn(merchant);
        when(paymentLogQueryMapper.findByOrderNumberOrderByIdAsc("ORD-REFUND")).thenReturn(List.of(
                paymentLog(merchant, "95.00", "INCOME", "product income"),
                paymentLog(admin, "5.00", "INCOME", "order commission")
        ));

        orderService.approveRefund(105L, merchant);

        assertThat(order.getStatus()).isEqualTo("REFUNDED");
        assertThat(order.getRefundReason()).isEqualTo("quality issue");
        assertThat(merchant.getWalletBalance()).isEqualByComparingTo("0.00");
        assertThat(admin.getWalletBalance()).isEqualByComparingTo("0.00");
        assertThat(buyer.getWalletBalance()).isEqualByComparingTo("100.00");

        verify(customerOrderQueryMapper).update(order);
        List<PaymentLog> logs = captureInsertedLogs(3);
        assertThat(logs)
                .extracting(PaymentLog::getType, PaymentLog::getRemark, log -> log.getUser().getId(), PaymentLog::getAmount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("REFUND", "order refund deduction", 2L, new BigDecimal("-95.00")),
                        org.assertj.core.groups.Tuple.tuple("REFUND", "order commission reversal", 3L, new BigDecimal("-5.00")),
                        org.assertj.core.groups.Tuple.tuple("REFUND", "merchant approved refund", 1L, new BigDecimal("100.00"))
                );
    }

    @Test
    void rejectRefundRestoresOriginalStatus() {
        User merchant = user(2L, "merchant", "MERCHANT", "95.00");

        CustomerOrder order = new CustomerOrder();
        order.setId(106L);
        order.setOrderNumber("ORD-REFUND-REJECT");
        order.setStatus("REFUND_REQUESTED");
        order.setRefundReason(refundReason("PENDING_ADMIN", "keep order"));
        order.setItems(List.of(orderItem(product(11L, merchant, "100.00", 8), 1, "100.00")));

        when(customerOrderQueryMapper.selectById(106L)).thenReturn(order);

        orderService.rejectRefund(106L, merchant);

        assertThat(order.getStatus()).isEqualTo("PLACED");
        assertThat(order.getRefundReason()).isNull();
        verify(customerOrderQueryMapper).update(order);
    }

    private List<PaymentLog> captureInsertedLogs(int expectedTimes) {
        ArgumentCaptor<PaymentLog> captor = ArgumentCaptor.forClass(PaymentLog.class);
        verify(paymentLogQueryMapper, times(expectedTimes)).insert(captor.capture());
        return captor.getAllValues();
    }

    private CreateOrderRequest createRequest(String payMethod, Long productId, int quantity) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("Buyer");
        request.setPhone("13800000000");
        request.setAddress("Shanghai");
        request.setPayMethod(payMethod);

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        request.setItems(List.of(item));
        return request;
    }

    private CreateOrderRequest.OrderItemRequest orderItemRequest(Long productId, int quantity, String sizeLabel) {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setSizeLabel(sizeLabel);
        return item;
    }

    private OrderItem orderItem(Product product, int quantity, String unitPrice) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(unitPrice));
        return item;
    }

    private PaymentLog paymentLog(User user, String amount, String type, String remark) {
        PaymentLog log = new PaymentLog();
        log.setUser(user);
        log.setAmount(new BigDecimal(amount));
        log.setType(type);
        log.setRemark(remark);
        return log;
    }

    private Product product(Long id, User owner, String price, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setName("Jacket");
        product.setOwner(owner);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        return product;
    }

    private User user(Long id, String username, String role, String balance) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setWalletBalance(new BigDecimal(balance));
        return user;
    }

    private String refundReason(String status, String reason) {
        return "[[REFUND_ORIGIN=" + status + "]]" + reason;
    }
}
