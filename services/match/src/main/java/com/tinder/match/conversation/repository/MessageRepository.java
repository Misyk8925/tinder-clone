package com.tinder.match.conversation.repository;

import com.tinder.match.conversation.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationConversationIdOrderByCreatedAtAsc(UUID conversationId);

    Optional<Message> findBySenderIdAndClientMessageId(UUID senderId, UUID clientMessageId);
}
