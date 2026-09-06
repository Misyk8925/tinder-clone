package com.tinder.profiles.infrastructure.external.moderation;

import com.tinder.profiles.application.moderation.ModerationContentType;
import com.tinder.profiles.application.moderation.ModerationDecision;
import com.tinder.profiles.application.moderation.ModerationPort;
import com.tinder.profiles.config.props.ModerationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class ModerationServiceAdapter implements ModerationPort {

    private final WebClient client;
    private final ModerationProperties properties;

    public ModerationServiceAdapter(
            @Qualifier("moderationWebClient") WebClient client,
            ModerationProperties properties
    ) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public ModerationDecision moderateText(String contentId, ModerationContentType type, String text, String authorId) {
        if (!properties.enabled() || text == null || text.isBlank()) {
            return ModerationDecision.allow();
        }
        return execute(new ModerationRequest(contentId, type.name(), text.strip(), List.of(), authorId));
    }

    @Override
    public ModerationDecision moderateImages(String contentId, ModerationContentType type, List<String> imageUrls, String authorId) {
        if (!properties.enabled() || imageUrls == null || imageUrls.isEmpty()) {
            return ModerationDecision.allow();
        }
        return execute(new ModerationRequest(contentId, type.name(), null, imageUrls, authorId));
    }

    private ModerationDecision execute(ModerationRequest request) {
        try {
            ModerationResponse response = client.post()
                    .uri("/internal/v1/moderations")
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ModerationResponse.class)
                    .timeout(properties.service().timeout().plus(Duration.ofMillis(250)))
                    .block();
            if (response == null || response.decision() == null) {
                log.warn("Moderation returned an empty decision for {}", request.contentId());
                return ModerationDecision.allow();
            }
            return new ModerationDecision(response.decision(), response.reason());
        } catch (WebClientResponseException error) {
            log.warn("Moderation HTTP {} for {}: {}", error.getStatusCode().value(), request.contentId(), error.getMessage());
            return ModerationDecision.allow();
        } catch (RuntimeException error) {
            log.warn("Moderation unavailable for {}: {}", request.contentId(), error.getMessage());
            return ModerationDecision.allow();
        }
    }

    private String basicAuth() {
        String username = properties.service().username() == null ? "" : properties.service().username();
        String password = properties.service().password() == null ? "" : properties.service().password();
        String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    public record ModerationRequest(
            String contentId,
            String contentType,
            String text,
            List<String> imageUrls,
            String authorId
    ) {
    }

    public record ModerationResponse(String decision, String reason) {
    }
}
