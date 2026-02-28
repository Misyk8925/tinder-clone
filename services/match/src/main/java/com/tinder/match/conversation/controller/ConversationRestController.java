package com.tinder.match.conversation.controller;

import com.tinder.match.conversation.ConversationService;
import com.tinder.match.conversation.dto.ConversationDto;
import com.tinder.match.conversation.dto.CreateConversationRequest;
import com.tinder.match.conversation.dto.MessageDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/rest/conversations")
@RequiredArgsConstructor
public class ConversationRestController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ConversationDto> createConversation(@Valid @RequestBody CreateConversationRequest request) {
        ConversationDto conversation = conversationService.createConversation(
                request.participant1Id(),
                request.participant2Id()
        );
        return ResponseEntity.ok(conversation);
    }

    @PostMapping(value = "/{conversationId}/messages/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageDto> sendPhotoMessage(
            @PathVariable UUID conversationId,
            @RequestParam UUID senderId,
            @RequestParam(required = false) UUID clientMessageId,
            @RequestPart("file") MultipartFile file
    ) {
        UUID resolvedClientMessageId = clientMessageId != null ? clientMessageId : UUID.randomUUID();
        MessageDto message = conversationService.sendPhotoMessage(senderId, conversationId, resolvedClientMessageId, file);
        return ResponseEntity.ok(message);
    }
}
