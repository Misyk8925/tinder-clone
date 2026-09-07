package com.tinder.match.conversation.controller;

import com.tinder.match.conversation.ConversationService;
import com.tinder.match.conversation.dto.ConversationDto;
import com.tinder.match.conversation.dto.ConversationWithMessagesDto;
import com.tinder.match.conversation.dto.CreateConversationRequest;
import com.tinder.match.conversation.dto.MessageDto;
import com.tinder.match.security.UserProfileMappingService;
import com.tinder.match.security.AuthenticatedProfileResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/rest/conversations")
@RequiredArgsConstructor
public class ConversationRestController {

    private final ConversationService conversationService;
    private final UserProfileMappingService userProfileMappingService;
    private final AuthenticatedProfileResolver authenticatedProfileResolver;

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationWithMessagesDto> getConversation(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID callerProfileId = authenticatedProfileResolver.resolve(jwt);
        ConversationWithMessagesDto conv = conversationService.getConversation(conversationId);

        // Register the caller's Keycloak user ID → profile ID mapping so the
        // WS controller can resolve the correct participant when they send messages.
        boolean isParticipant = callerProfileId.equals(conv.participant1Id())
                || callerProfileId.equals(conv.participant2Id());
        if (!isParticipant) {
            throw new AccessDeniedException("Caller is not a participant of this conversation");
        }

        return ResponseEntity.ok(conv);
    }

    @PostMapping
    public ResponseEntity<ConversationDto> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID callerProfileId = authenticatedProfileResolver.resolve(jwt);
        if (!callerProfileId.equals(request.participant1Id())
                && !callerProfileId.equals(request.participant2Id())) {
            throw new AccessDeniedException("Caller must be a conversation participant");
        }
        ConversationDto conversation = conversationService.createConversation(
                request.participant1Id(),
                request.participant2Id()
        );

        // By convention the caller always passes their own profile ID as participant1Id.
        userProfileMappingService.register(jwt.getSubject(), callerProfileId);

        return ResponseEntity.ok(conversation);
    }

    @PostMapping(value = "/{conversationId}/messages/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageDto> sendPhotoMessage(
            @PathVariable UUID conversationId,
            @RequestParam(required = false) UUID clientMessageId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID senderId = authenticatedProfileResolver.resolve(jwt);
        UUID resolvedClientMessageId = clientMessageId != null ? clientMessageId : UUID.randomUUID();
        MessageDto message = conversationService.sendPhotoMessage(senderId, conversationId, resolvedClientMessageId, file);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/my-chats")
    public ResponseEntity<List<ConversationDto>> getMyChats(@AuthenticationPrincipal Jwt jwt) {
        UUID profileId = authenticatedProfileResolver.resolve(jwt);
        List<ConversationDto> conversations = conversationService.getMyChats(profileId);
        return ResponseEntity.ok(conversations);
    }
}
