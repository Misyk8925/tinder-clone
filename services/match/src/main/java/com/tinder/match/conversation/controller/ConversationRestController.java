package com.tinder.match.conversation.controller;

import com.tinder.match.conversation.ConversationService;
import com.tinder.match.conversation.dto.ConversationDto;
import com.tinder.match.conversation.dto.ConversationWithMessagesDto;
import com.tinder.match.conversation.dto.CreateConversationRequest;
import com.tinder.match.conversation.dto.MessageDto;
import com.tinder.match.security.CallerProfileResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/**
 * Conversation REST API.
 * <p>
 * Every endpoint acts as the profile that the caller's JWT actually owns, resolved through
 * {@link CallerProfileResolver}. Profile IDs that arrive in the request are only ever accepted
 * as a cross-check against that resolved identity, never as the identity itself.
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
            @RequestParam(required = false) UUID callerProfileId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID viewerId = callerProfileResolver.requireProfileId(jwt);
        requireMatchesCaller(callerProfileId, viewerId);
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

        ConversationDto conversation = conversationService.createConversation(
                request.participant1Id(),
                request.participant2Id()
        );

        return ResponseEntity.ok(conversation);
    }

    @PostMapping(value = "/{conversationId}/messages/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageDto> sendPhotoMessage(
            @PathVariable UUID conversationId,
            @RequestParam(required = false) UUID senderId,
            @RequestParam(required = false) UUID clientMessageId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID authenticatedSenderId = callerProfileResolver.requireProfileId(jwt);
        requireMatchesCaller(senderId, authenticatedSenderId);
        UUID resolvedClientMessageId = clientMessageId != null ? clientMessageId : UUID.randomUUID();
        MessageDto message = conversationService.sendPhotoMessage(
                authenticatedSenderId, conversationId, resolvedClientMessageId, file);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/my-chats")
    public ResponseEntity<List<ConversationDto>> getMyChats(
            @RequestParam(required = false) UUID profileId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID callerId = callerProfileResolver.requireProfileId(jwt);
        requireMatchesCaller(profileId, callerId);
        return ResponseEntity.ok(conversationService.getMyChats(callerId));
    }

    /**
     * Legacy clients still send their own profile ID alongside the token. It is optional, but when
     * present it must agree with the token — a mismatch is an attempt to act as somebody else.
     */
    private void requireMatchesCaller(UUID claimedProfileId, UUID callerProfileId) {
        if (claimedProfileId != null && !claimedProfileId.equals(callerProfileId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "The supplied profile id does not belong to the authenticated user");
        }
    }
}
