package com.aistudyassistant.repository;

import com.aistudyassistant.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop10ByConversationIdOrderByIdDesc(Long conversationId);

    List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    List<ChatMessage> findByUserIdOrderByIdAsc(Long userId);

    List<ChatMessage> findTop10ByUserIdOrderByIdDesc(Long userId);

    @Transactional
    void deleteByUserId(Long userId);

    @Transactional
    void deleteByConversationId(Long conversationId);
}