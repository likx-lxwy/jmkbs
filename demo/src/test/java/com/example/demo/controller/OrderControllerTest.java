package com.example.demo.controller;

import com.example.demo.model.CustomerOrder;
import com.example.demo.model.User;
import com.example.demo.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService)).build();
    }

    @Test
    void createBatchUsesCurrentUserFromRequest() throws Exception {
        User buyer = user(1L, "buyer", "USER");
        CustomerOrder orderA = order(11L, "ORD-A", "PENDING_PAYMENT", "100.00", "ALIPAY");
        CustomerOrder orderB = order(12L, "ORD-B", "PENDING_PAYMENT", "50.00", "ALIPAY");

        when(orderService.createOrdersByMerchant(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(buyer)))
                .thenReturn(List.of(orderA, orderB));
        when(orderService.toResponse(orderA)).thenReturn(new com.example.demo.dto.OrderResponse(
                11L, "ORD-A", "PENDING_PAYMENT", new BigDecimal("100.00"), List.of(), LocalDateTime.now(), "ALIPAY", null
        ));
        when(orderService.toResponse(orderB)).thenReturn(new com.example.demo.dto.OrderResponse(
                12L, "ORD-B", "PENDING_PAYMENT", new BigDecimal("50.00"), List.of(), LocalDateTime.now(), "ALIPAY", null
        ));

        mockMvc.perform(post("/api/orders/batch")
                        .requestAttr("CURRENT_USER", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerName":"Buyer",
                                  "phone":"13800000000",
                                  "address":"Shanghai",
                                  "payMethod":"ALIPAY",
                                  "items":[
                                    {"productId":11,"quantity":1},
                                    {"productId":12,"quantity":1}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCount").value(2))
                .andExpect(jsonPath("$.totalAmount").value(150.00))
                .andExpect(jsonPath("$.orders[0].orderNumber").value("ORD-A"))
                .andExpect(jsonPath("$.orders[1].orderNumber").value("ORD-B"));

        verify(orderService).createOrdersByMerchant(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(buyer));
    }

    @Test
    void createBatchRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/orders/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerName":"Buyer",
                                  "phone":"13800000000",
                                  "address":"Shanghai",
                                  "payMethod":"ALIPAY",
                                  "items":[{"productId":11,"quantity":1}]
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void confirmReceiptUsesCurrentUserFromRequest() throws Exception {
        User buyer = user(1L, "buyer", "USER");

        mockMvc.perform(post("/api/orders/12/confirm-receipt")
                        .requestAttr("CURRENT_USER", buyer))
                .andExpect(status().isOk());

        verify(orderService).confirmReceipt(12L, buyer);
    }

    @Test
    void confirmReceiptRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/orders/12/confirm-receipt"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void refundPassesReasonToService() throws Exception {
        User buyer = user(2L, "buyer2", "USER");

        mockMvc.perform(post("/api/orders/18/refund")
                        .requestAttr("CURRENT_USER", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"size mismatch"}
                                """))
                .andExpect(status().isOk());

        verify(orderService).refundOrder(18L, buyer, "size mismatch");
    }

    @Test
    void refundWithoutLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/orders/18/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void approveRefundUsesCurrentMerchantFromRequest() throws Exception {
        User merchant = user(3L, "merchant", "MERCHANT");

        mockMvc.perform(post("/api/orders/20/refund/approve")
                        .requestAttr("CURRENT_USER", merchant))
                .andExpect(status().isOk());

        verify(orderService).approveRefund(20L, merchant);
    }

    @Test
    void approveRefundRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/orders/20/refund/approve"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void rejectRefundUsesCurrentMerchantFromRequest() throws Exception {
        User merchant = user(4L, "merchant2", "MERCHANT");

        mockMvc.perform(post("/api/orders/21/refund/reject")
                        .requestAttr("CURRENT_USER", merchant))
                .andExpect(status().isOk());

        verify(orderService).rejectRefund(21L, merchant);
    }

    @Test
    void rejectRefundRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/orders/21/refund/reject"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    private CustomerOrder order(Long id, String orderNumber, String status, String amount, String payMethod) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setOrderNumber(orderNumber);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal(amount));
        order.setPayMethod(payMethod);
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }

    private User user(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }
}
