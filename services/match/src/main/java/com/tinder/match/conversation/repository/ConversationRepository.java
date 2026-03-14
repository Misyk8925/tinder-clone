package com.tinder.match.conversation.repository;

import com.tinder.match.conversation.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByParticipant1IdAndParticipant2Id(UUID participant1Id, UUID participant2Id);

    List<Conversation> findAllByParticipant1IdOrParticipant2Id(UUID participant1Id, UUID participant2Id);
}
