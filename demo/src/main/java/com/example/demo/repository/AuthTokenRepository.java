package com.example.demo.repository;

import com.example.demo.model.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByToken(String token);

    @Query("select t from AuthToken t join fetch t.user where t.token = :token")
    Optional<AuthToken> findByTokenWithUser(@Param("token") String token);

    void deleteByExpiresAtBefore(LocalDateTime time);
}
