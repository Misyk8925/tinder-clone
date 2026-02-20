package com.tinder.match.conversation.model;

import jakarta.persistence.*;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(
        name = "conversations",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_conversations_participants",
                columnNames = {"participant1_id", "participant2_id"}
        )
)
@Getter
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "participant1_id", nullable = false)
    private UUID participant1Id;

    @Column(name = "participant2_id", nullable = false)
    private UUID participant2Id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ConversationStatus status;

    public static Conversation createActive(UUID firstParticipantId, UUID secondParticipantId) {
        UUID participant1 = firstParticipantId;
        UUID participant2 = secondParticipantId;

        if (participant1.compareTo(participant2) > 0) {
            participant1 = secondParticipantId;
            participant2 = firstParticipantId;
        }

        Conversation conversation = new Conversation();
        conversation.participant1Id = participant1;
        conversation.participant2Id = participant2;
        conversation.status = ConversationStatus.ACTIVE;
        return conversation;
    }

}
