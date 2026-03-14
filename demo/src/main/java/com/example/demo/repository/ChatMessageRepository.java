package com.example.demo.repository;

import com.example.demo.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @Query("select m from ChatMessage m where m.product.id = :productId and ((m.sender.id = :userId and m.receiver.id = :targetId) or (m.sender.id = :targetId and m.receiver.id = :userId)) order by m.createdAt asc")
    List<ChatMessage> findConversation(@Param("productId") Long productId, @Param("userId") Long userId, @Param("targetId") Long targetId);

    @Query("select m from ChatMessage m where m.sender.id = :userId or m.receiver.id = :userId order by m.createdAt desc")
    List<ChatMessage> findRecent(@Param("userId") Long userId);

    void deleteByProductId(Long productId);
}
