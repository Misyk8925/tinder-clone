package com.tinder.match.conversation.implementations;

import com.tinder.match.conversation.ConversationService;
import com.tinder.match.conversation.dto.ConversationDto;
import com.tinder.match.conversation.dto.ConversationWithMessagesDto;
import com.tinder.match.conversation.dto.LastMessagePreviewDto;
import com.tinder.match.conversation.dto.MessageAttachmentDto;
import com.tinder.match.conversation.dto.MessageDto;
import com.tinder.match.conversation.dto.MessageHistoryDto;
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
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
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
    private final ConversationPhotoStorageService conversationPhotoStorageService;

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
    public ConversationWithMessagesDto getConversation(UUID conversationId) {
        log.info("Get conversation requested conversationId={}", conversationId);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new MessagingException("Conversation not found"));

        List<MessageHistoryDto> messages = messageRepository
                .findByConversationConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toHistoryDto)
                .toList();

        log.info("Get conversation found {} messages for conversationId={}", messages.size(), conversationId);
        return new ConversationWithMessagesDto(
                conversation.getConversationId(),
                conversation.getParticipant1Id(),
                conversation.getParticipant2Id(),
                conversation.getStatus(),
                messages
        );
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

        if (msg.messageType() != MessageType.TEXT) {
            for (MessageAttachmentDto attachment : msg.attachments()) {
                message.addAttachment(toAttachmentEntity(attachment));
            }
        }

        Message saved = persistAndPublish(message);

        return toDto(saved);
    }

    @Override
    @Transactional
    public MessageDto sendPhotoMessage(UUID senderId, UUID conversationId, UUID clientMessageId, MultipartFile file) {
        log.info(
                "Send photo message requested senderId={} conversationId={} clientMessageId={}",
                senderId,
                conversationId,
                clientMessageId
        );
        if (senderId == null) {
            throw new MessagingException("Sender id is required");
        }
        if (conversationId == null) {
            throw new MessagingException("Conversation id is required");
        }
        if (clientMessageId == null) {
            throw new MessagingException("Client message id is required");
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new MessagingException("Conversation not found"));
        validateConversationAccess(conversation, senderId);

        Optional<Message> duplicate = messageRepository.findBySenderIdAndClientMessageId(senderId, clientMessageId);
        if (duplicate.isPresent()) {
            log.info("Duplicate photo message ignored for sender={} clientMessageId={}", senderId, clientMessageId);
            return toDto(duplicate.get());
        }

        ConversationPhotoStorageService.UploadedPhoto uploadedPhoto =
                conversationPhotoStorageService.uploadPhoto(file, conversationId, senderId, clientMessageId);

        Message message = Message.builder()
                .clientMessageId(clientMessageId)
                .conversation(conversation)
                .senderId(senderId)
                .type(MessageType.IMAGE)
                .text(null)
                .build();

        message.addAttachment(MessageAttachment.builder()
                .storageKey(uploadedPhoto.storageKey())
                .url(uploadedPhoto.url())
                .mimeType(uploadedPhoto.mimeType())
                .sizeBytes(uploadedPhoto.sizeBytes())
                .originalName(uploadedPhoto.originalName())
                .width(uploadedPhoto.width())
                .height(uploadedPhoto.height())
                .sha256(uploadedPhoto.sha256())
                .build());

        Message saved = persistAndPublish(message);
        return toDto(saved);
    }

    public List<ConversationDto> getMyChats(UUID profileId) {
        log.info("Get my chats requested profile id={}", profileId);
        List<Conversation> conversations = conversationRepository.findAllByParticipant1IdOrParticipant2Id(profileId, profileId);
        log.info("Get my chats found {} conversations for profile id={}", conversations.size(), profileId);
        return conversations.stream()
                .map(conv -> toConversationDtoWithPreview(conv))
                .toList();
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

        for (MessageAttachmentDto attachment : msg.attachments()) {
            validateAttachmentPayload(attachment);
        }
    }

    private void validateAttachmentPayload(MessageAttachmentDto attachment) {
        if (attachment == null) {
            throw new MessagingException("Attachment payload is required");
        }
        if (attachment.url() == null || attachment.url().isBlank()) {
            throw new MessagingException("Attachment URL is required");
        }
        if (attachment.mimeType() == null || attachment.mimeType().isBlank()) {
            throw new MessagingException("Attachment mimeType is required");
        }
        if (attachment.sizeBytes() == null || attachment.sizeBytes() <= 0) {
            throw new MessagingException("Attachment sizeBytes must be greater than zero");
        }
    }

    private MessageAttachment toAttachmentEntity(MessageAttachmentDto attachment) {
        return MessageAttachment.builder()
                .storageKey(resolveStorageKey(attachment))
                .url(attachment.url())
                .mimeType(attachment.mimeType())
                .sizeBytes(attachment.sizeBytes())
                .originalName(attachment.originalName())
                .width(attachment.width())
                .height(attachment.height())
                .durationMs(attachment.durationMs())
                .build();
    }

    private String resolveStorageKey(MessageAttachmentDto attachment) {
        if (attachment.storageKey() != null && !attachment.storageKey().isBlank()) {
            return attachment.storageKey();
        }

        try {
            URI uri = URI.create(attachment.url());
            String path = uri.getPath();
            if (path != null && !path.isBlank()) {
                return path.startsWith("/") ? path.substring(1) : path;
            }
        } catch (IllegalArgumentException ignored) {
            // URL may already be a plain storage key.
        }

        return attachment.url();
    }

    private Message persistAndPublish(Message message) {
        Message saved = messageRepository.save(message);
        log.info(
                "Send message persisted messageId={} conversationId={} senderId={} clientMessageId={}",
                saved.getMessageId(),
                saved.getConversation().getConversationId(),
                saved.getSenderId(),
                saved.getClientMessageId()
        );

        publishMessageCreatedEvent(saved);
        return saved;
    }

    private void publishMessageCreatedEvent(Message message) {
        MessageDto dto = toDto(message);
        eventPublisher.publishEvent(new MessageCreatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                message.getMessageId(),
                message.getConversation().getConversationId(),
                message.getSenderId(),
                message.getClientMessageId(),
                0L,
                message.getType(),
                message.getText(),
                dto.attachments()
        ));
        log.info(
                "Send message published event messageId={} conversationId={} senderId={}",
                message.getMessageId(),
                message.getConversation().getConversationId(),
                message.getSenderId()
        );
    }

    private MessageHistoryDto toHistoryDto(Message message) {
        List<MessageAttachmentDto> attachments = message.getAttachments() == null
                ? List.of()
                : message.getAttachments().stream()
                .map(attachment -> new MessageAttachmentDto(
                        attachment.getStorageKey(),
                        attachment.getUrl(),
                        attachment.getMimeType(),
                        attachment.getSizeBytes(),
                        attachment.getOriginalName(),
                        attachment.getWidth(),
                        attachment.getHeight(),
                        attachment.getDurationMs()
                ))
                .toList();

        return new MessageHistoryDto(
                message.getMessageId(),
                message.getSenderId(),
                message.getType(),
                message.getText(),
                attachments,
                message.getCreatedAt()
        );
    }

    private MessageDto toDto(Message message) {
        List<MessageAttachmentDto> attachments = message.getAttachments() == null
                ? List.of()
                : message.getAttachments().stream()
                .map(attachment -> new MessageAttachmentDto(
                        attachment.getStorageKey(),
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
                conversation.getStatus(),
                null
        );
    }

    private ConversationDto toConversationDtoWithPreview(Conversation conversation) {
        LastMessagePreviewDto lastMessage = messageRepository
                .findTopByConversationConversationIdOrderByCreatedAtDesc(conversation.getConversationId())
                .map(m -> new LastMessagePreviewDto(
                        m.getMessageId(),
                        m.getSenderId(),
                        m.getType(),
                        m.getText(),
                        m.getCreatedAt()
                ))
                .orElse(null);

        return new ConversationDto(
                conversation.getConversationId(),
                conversation.getParticipant1Id(),
                conversation.getParticipant2Id(),
                conversation.getStatus(),
                lastMessage
        );
    }
}
