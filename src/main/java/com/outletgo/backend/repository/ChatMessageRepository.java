package com.outletgo.backend.repository;

import com.outletgo.backend.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByStoreIdOrderBySentAtAsc(UUID storeId);
    List<ChatMessage> findByConversationIdOrderBySentAtAsc(UUID conversationId);

    @Query("SELECT m FROM ChatMessage m WHERE m.sender.id = :userId OR m.receiver.id = :userId ORDER BY m.sentAt ASC")
    List<ChatMessage> findBySenderIdOrReceiverIdOrderBySentAtAsc(@Param("userId") UUID userId);
}
