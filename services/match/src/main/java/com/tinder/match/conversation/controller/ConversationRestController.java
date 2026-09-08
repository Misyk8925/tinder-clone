package com.tinder.match.conversation.controller;

import com.tinder.match.conversation.ConversationService;
import com.tinder.match.conversation.dto.ConversationDto;
import com.tinder.match.conversation.dto.ConversationWithMessagesDto;
import com.tinder.match.conversation.dto.CreateConversationRequest;
import com.tinder.match.conversation.dto.MessageDto;
import com.tinder.match.security.CallerProfileResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Conversation REST API.
 * <p>
 * Every endpoint acts as the profile the caller's JWT owns, resolved through
 * {@link CallerProfileResolver}. There is deliberately no way for a request to name the profile
 * it acts as: the token is the only source of identity, so there is nothing to cross-check and
 * nothing to get wrong.
 */
@RestController
@RequestMapping("/rest/conversations")
@RequiredArgsConstructor
public class ConversationRestController {

    private final ConversationService conversationService;
    private final CallerProfileResolver callerProfileResolver;

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationWithMessagesDto> getConversation(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID viewerId = callerProfileResolver.requireProfileId(jwt);
        return ResponseEntity.ok(conversationService.getConversation(conversationId, viewerId));
    }

    @PostMapping
    public ResponseEntity<ConversationDto> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID callerId = callerProfileResolver.requireProfileId(jwt);
        // A conversation may only be opened by one of its own participants.
        if (!callerId.equals(request.participant1Id()) && !callerId.equals(request.participant2Id())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "A conversation can only be created by one of its participants");
        }

        return ResponseEntity.ok(conversationService.createConversation(
                request.participant1Id(),
                request.participant2Id()));
    }

    @PostMapping(value = "/{conversationId}/messages/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageDto> sendPhotoMessage(
            @PathVariable UUID conversationId,
            @RequestParam(required = false) UUID clientMessageId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID senderId = callerProfileResolver.requireProfileId(jwt);
        UUID resolvedClientMessageId = clientMessageId != null ? clientMessageId : UUID.randomUUID();
        return ResponseEntity.ok(conversationService.sendPhotoMessage(
                senderId, conversationId, resolvedClientMessageId, file));
    }

    @GetMapping("/my-chats")
    public ResponseEntity<List<ConversationDto>> getMyChats(@AuthenticationPrincipal Jwt jwt) {
        UUID callerId = callerProfileResolver.requireProfileId(jwt);
        return ResponseEntity.ok(conversationService.getMyChats(callerId));
    }
}
