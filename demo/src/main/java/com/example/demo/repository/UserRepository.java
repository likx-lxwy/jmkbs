package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    long countByRole(String role);

    long countByRoleAndMerchantStatus(String role, String merchantStatus);

    List<User> findByRoleOrderByIdDesc(String role);

    List<User> findByRoleAndMerchantStatusOrderByIdDesc(String role, String merchantStatus);

    Optional<User> findFirstByRoleOrderByIdAsc(String role);
}
