package com.aistudyassistant.repository;

import com.aistudyassistant.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}