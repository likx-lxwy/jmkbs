package com.example.demo.controller;

import com.example.demo.mapper.CustomerOrderQueryMapper;
import com.example.demo.mapper.RefundChatMessageQueryMapper;
import com.example.demo.model.CustomerOrder;
import com.example.demo.model.OrderItem;
import com.example.demo.model.Product;
import com.example.demo.model.RefundChatMessage;
import com.example.demo.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RefundChatControllerTest {

    @Mock
    private RefundChatMessageQueryMapper refundChatMessageQueryMapper;

    @Mock
    private CustomerOrderQueryMapper customerOrderQueryMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new RefundChatController(refundChatMessageQueryMapper, customerOrderQueryMapper)
        ).build();
    }

    @Test
    void listRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/orders/31/refund-chat"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(refundChatMessageQueryMapper, customerOrderQueryMapper);
    }

    @Test
    void sendCreatesRefundChatMessageBetweenBuyerAndMerchant() throws Exception {
        User buyer = user(1L, "buyer", "USER");
        User merchant = user(2L, "merchant", "MERCHANT");
        CustomerOrder order = refundOrder(31L, "REFUND_REQUESTED", buyer, merchant);

        when(customerOrderQueryMapper.selectById(31L)).thenReturn(order);
        when(refundChatMessageQueryMapper.countByOrderId(31L)).thenReturn(0L);

        mockMvc.perform(post("/api/orders/31/refund-chat")
                        .requestAttr("CURRENT_USER", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"这件商品尺码不合适，麻烦尽快处理退款"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<RefundChatMessage> captor = ArgumentCaptor.forClass(RefundChatMessage.class);
        verify(refundChatMessageQueryMapper).insert(captor.capture());

        RefundChatMessage message = captor.getValue();
        assertThat(message.getOrder().getId()).isEqualTo(31L);
        assertThat(message.getSender().getId()).isEqualTo(1L);
        assertThat(message.getReceiver().getId()).isEqualTo(2L);
        assertThat(message.getContent()).isEqualTo("这件商品尺码不合适，麻烦尽快处理退款");
    }

    @Test
    void sendRejectsNonRefundOrderWithoutExistingConversation() throws Exception {
        User buyer = user(1L, "buyer", "USER");
        User merchant = user(2L, "merchant", "MERCHANT");
        CustomerOrder order = refundOrder(32L, "APPROVED", buyer, merchant);

        when(customerOrderQueryMapper.selectById(32L)).thenReturn(order);
        when(refundChatMessageQueryMapper.countByOrderId(32L)).thenReturn(0L);

        mockMvc.perform(post("/api/orders/32/refund-chat")
                        .requestAttr("CURRENT_USER", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"想继续沟通退款"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private CustomerOrder refundOrder(Long orderId, String status, User buyer, User merchant) {
        CustomerOrder order = new CustomerOrder();
        order.setId(orderId);
        order.setStatus(status);
        order.setBuyer(buyer);
        order.setItems(List.of(orderItem(merchant)));
        return order;
    }

    private OrderItem orderItem(User merchant) {
        Product product = new Product();
        product.setId(8L);
        product.setOwner(merchant);

        OrderItem item = new OrderItem();
        item.setProduct(product);
        return item;
    }

    private User user(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }
}
