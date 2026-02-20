package com.tinder.match.conversation.implementations;

import com.tinder.match.conversation.ConversationService;
import com.tinder.match.conversation.dto.ConversationDto;
import com.tinder.match.conversation.dto.MessageAttachmentDto;
import com.tinder.match.conversation.dto.MessageDto;
import com.tinder.match.conversation.event.MessageCreatedEvent;
import com.tinder.match.conversation.model.Conversation;
import com.tinder.match.conversation.model.ConversationStatus;
import com.tinder.match.conversation.model.Message;
import com.tinder.match.conversation.model.MessageAttachment;
import com.tinder.match.conversation.model.MessageType;
import com.tinder.match.conversation.repository.ConversationRepository;
import com.tinder.match.conversation.repository.MessageRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationServiceImpl implements ConversationService {

    private final ApplicationEventPublisher eventPublisher;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Override
    @Transactional
    public ConversationDto createConversation(UUID firstParticipantId, UUID secondParticipantId) {
        log.info("Create conversation requested participant1={} participant2={}", firstParticipantId, secondParticipantId);
        if (firstParticipantId == null || secondParticipantId == null) {
            throw new MessagingException("Both participants are required");
        }
        if (firstParticipantId.equals(secondParticipantId)) {
            throw new MessagingException("Participants must be different users");
        }

        UUID participant1 = firstParticipantId;
        UUID participant2 = secondParticipantId;
        if (participant1.compareTo(participant2) > 0) {
            participant1 = secondParticipantId;
            participant2 = firstParticipantId;
        }

        Optional<Conversation> existing = conversationRepository
                .findByParticipant1IdAndParticipant2Id(participant1, participant2);
        if (existing.isPresent()) {
            log.info(
                    "Create conversation reused existing conversationId={} participant1={} participant2={}",
                    existing.get().getConversationId(),
                    participant1,
                    participant2
            );
            return toConversationDto(existing.get());
        }

        Conversation saved = conversationRepository.save(Conversation.createActive(participant1, participant2));
        log.info(
                "Create conversation created conversationId={} participant1={} participant2={}",
                saved.getConversationId(),
                saved.getParticipant1Id(),
                saved.getParticipant2Id()
        );
        return toConversationDto(saved);
    }

    @Override
    @Transactional
    public MessageDto sendMessage(UUID senderId, MessageDto msg) {
        log.info(
                "Send message requested senderId={} conversationId={} clientMessageId={} type={}",
                senderId,
                msg != null ? msg.conversationId() : null,
                msg != null ? msg.clientMessageId() : null,
                msg != null ? msg.messageType() : null
        );
        if (msg == null) {
            throw new MessagingException("Message payload is required");
        }

        Conversation conversation = conversationRepository.findById(msg.conversationId())
                .orElseThrow(() -> new MessagingException("Conversation not found"));
        log.debug(
                "Send message loaded conversationId={} status={} participant1={} participant2={}",
                conversation.getConversationId(),
                conversation.getStatus(),
                conversation.getParticipant1Id(),
                conversation.getParticipant2Id()
        );

        validateConversationAccess(conversation, senderId);
        validateMessagePayload(msg);

        Optional<Message> duplicate = messageRepository.findBySenderIdAndClientMessageId(senderId, msg.clientMessageId());
        if (duplicate.isPresent()) {
            log.info("Duplicate message ignored for sender={} clientMessageId={}", senderId, msg.clientMessageId());
            return toDto(duplicate.get());
        }

        Message message = Message.builder()
                .clientMessageId(msg.clientMessageId())
                .conversation(conversation)
                .senderId(senderId)
                .type(msg.messageType())
                .text(msg.text())
                .build();

        if (msg.messageType() !=MessageType.TEXT) {
            // TODO implement for aws
        }

        Message saved = messageRepository.save(message);
        log.info(
                "Send message persisted messageId={} conversationId={} senderId={} clientMessageId={}",
                saved.getMessageId(),
                saved.getConversation().getConversationId(),
                saved.getSenderId(),
                saved.getClientMessageId()
        );
        eventPublisher.publishEvent(new MessageCreatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                saved.getMessageId(),
                saved.getConversation().getConversationId(),
                saved.getSenderId(),
                saved.getClientMessageId(),
                0L,
                saved.getType(),
                saved.getText()
        ));
        log.info(
                "Send message published event messageId={} conversationId={} senderId={}",
                saved.getMessageId(),
                saved.getConversation().getConversationId(),
                saved.getSenderId()
        );

        return toDto(saved);
    }

    private void validateConversationAccess(Conversation conversation, UUID senderId) {
        boolean isParticipant = senderId.equals(conversation.getParticipant1Id())
                || senderId.equals(conversation.getParticipant2Id());

        if (!isParticipant) {
            throw new MessagingException("Sender is not a participant of this conversation");
        }

        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new MessagingException("Conversation is not active");
        }
    }

    private void validateMessagePayload(MessageDto msg) {
        if (msg.messageType() == MessageType.TEXT) {
            boolean hasText = msg.text() != null && !msg.text().trim().isEmpty();
            boolean hasNoAttachments = msg.attachments() == null || msg.attachments().isEmpty();
            if (!hasText || !hasNoAttachments) {
                throw new MessagingException("TEXT message requires non-blank text and no attachments");
            }
            return;
        }

        if (msg.attachments() == null || msg.attachments().isEmpty()) {
            throw new MessagingException("Media message requires at least one attachment");
        }
    }

    private MessageDto toDto(Message message) {
        List<MessageAttachmentDto> attachments = message.getAttachments() == null
                ? List.of()
                : message.getAttachments().stream()
                .map(attachment -> new MessageAttachmentDto(
                        attachment.getUrl(),
                        attachment.getMimeType(),
                        attachment.getSizeBytes(),
                        attachment.getOriginalName(),
                        attachment.getWidth(),
                        attachment.getHeight(),
                        attachment.getDurationMs()
                ))
                .toList();

        return new MessageDto(
                message.getConversation().getConversationId(),
                message.getClientMessageId(),
                message.getType(),
                message.getText(),
                attachments
        );
    }

    private ConversationDto toConversationDto(Conversation conversation) {
        return new ConversationDto(
                conversation.getConversationId(),
                conversation.getParticipant1Id(),
                conversation.getParticipant2Id(),
                conversation.getStatus()
        );
    }
}
