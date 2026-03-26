package com.example.demo.controller;

import com.example.demo.mapper.UserQueryMapper;
import com.example.demo.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MerchantProfileControllerTest {

    @Mock
    private UserQueryMapper userQueryMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MerchantProfileController controller = new MerchantProfileController(userQueryMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void submitForReviewUpdatesMerchantInfoAndSetsPendingStatus() throws Exception {
        User currentMerchant = merchant(8L, "merchant_a", "UNREVIEWED");
        when(userQueryMapper.selectById(8L)).thenReturn(currentMerchant);

        mockMvc.perform(put("/api/merchant/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeName": "Shop A",
                                  "contactName": "Alice",
                                  "contactPhone": "13800000000",
                                  "businessAddress": "Shanghai Pudong",
                                  "licenseNumber": "LIC-001",
                                  "description": "Menswear",
                                  "submitForReview": true
                                }
                                """)
                        .requestAttr("CURRENT_USER", merchant(8L, "merchant_a", "UNREVIEWED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantStatus").value("PENDING"))
                .andExpect(jsonPath("$.storeName").value("Shop A"))
                .andExpect(jsonPath("$.contactName").value("Alice"));

        verify(userQueryMapper).update(argThat(matchesMerchantUpdate(
                "PENDING",
                "Shop A",
                "Alice",
                "13800000000",
                "Shanghai Pudong",
                "LIC-001"
        )));
    }

    @Test
    void submitForReviewRejectsIncompleteMerchantInfo() throws Exception {
        when(userQueryMapper.selectById(9L)).thenReturn(merchant(9L, "merchant_b", "UNREVIEWED"));

        mockMvc.perform(put("/api/merchant/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeName": "Shop B",
                                  "contactName": "Bob",
                                  "submitForReview": true
                                }
                                """)
                        .requestAttr("CURRENT_USER", merchant(9L, "merchant_b", "UNREVIEWED")))
                .andExpect(status().isBadRequest());

        verify(userQueryMapper, never()).update(any(User.class));
    }

    private ArgumentMatcher<User> matchesMerchantUpdate(String status,
                                                        String storeName,
                                                        String contactName,
                                                        String contactPhone,
                                                        String businessAddress,
                                                        String licenseNumber) {
        return user -> user != null
                && status.equals(user.getMerchantStatus())
                && storeName.equals(user.getMerchantStoreName())
                && contactName.equals(user.getMerchantContactName())
                && contactPhone.equals(user.getMerchantContactPhone())
                && businessAddress.equals(user.getMerchantBusinessAddress())
                && licenseNumber.equals(user.getMerchantLicenseNumber());
    }

    private User merchant(Long id, String username, String merchantStatus) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole("MERCHANT");
        user.setMerchantStatus(merchantStatus);
        return user;
    }
}
