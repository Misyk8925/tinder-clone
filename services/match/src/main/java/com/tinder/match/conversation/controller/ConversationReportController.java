package com.tinder.match.conversation.controller;

import com.tinder.match.conversation.ConversationService;
import com.tinder.match.conversation.dto.ConversationWithMessagesDto;
import com.tinder.match.moderation.ModerationClient;
import com.tinder.match.security.AuthenticatedProfileResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/rest/conversations")
@RequiredArgsConstructor
public class ConversationReportController {

    private final ConversationService conversations;
    private final ModerationClient moderation;
    private final AuthenticatedProfileResolver authenticatedProfileResolver;

    @PostMapping("/{conversationId}/report")
    public ResponseEntity<Map<String, String>> report(
            @PathVariable UUID conversationId,
            @Valid @RequestBody ReportConversationRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ConversationWithMessagesDto conversation = conversations.getConversation(conversationId);
        UUID reporterProfileId = authenticatedProfileResolver.resolve(jwt);
        if (!reporterProfileId.equals(conversation.participant1Id())
                && !reporterProfileId.equals(conversation.participant2Id())) {
            throw new AccessDeniedException("Only conversation participants may report it");
        }
        String reporter = jwt.getSubject();
        String target = request.messageId() == null ? conversationId.toString() : request.messageId().toString();
        String text = "Reported conversation " + conversation.conversationId()
                + " target=" + target + " (" + request.reason() + "): " + request.details();
        moderation.submitReport("report:conversation:" + conversationId + ":" + reporter, text, reporter);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", "accepted"));
    }

    public record ReportConversationRequest(
            UUID messageId,
            @NotBlank @Size(max = 64) String reason,
            @NotBlank @Size(max = 2000) String details
    ) {
    }
}
