package com.example.demo.repository;

import com.example.demo.model.PaymentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentLogRepository extends JpaRepository<PaymentLog, Long> {
    List<PaymentLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    @org.springframework.data.jpa.repository.Query("select p from PaymentLog p join p.user u where upper(u.role) = 'ADMIN' order by p.createdAt desc")
    List<PaymentLog> findAdminIncome();

    boolean existsByOrderNumberAndType(String orderNumber, String type);
}
