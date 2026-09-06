package com.tinder.match.moderation;

import com.tinder.match.config.ModerationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class ModerationClient {
    private final RestClient client;
    private final ModerationProperties properties;

    public ModerationClient(ModerationProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.service().timeout());
        factory.setReadTimeout(properties.service().timeout());
        this.client = RestClient.builder()
                .baseUrl(properties.service().url())
                .requestFactory(factory)
                .build();
    }

    public ModerationDecision moderateText(String contentId, String text, String authorId) {
        if (!properties.enabled() || text == null || text.isBlank()) {
            return ModerationDecision.allow();
        }
        return execute(new ModerationRequest(contentId, "MESSAGE", text.strip(), List.of(), authorId));
    }

    public ModerationDecision moderateImages(String contentId, List<String> imageUrls, String authorId) {
        if (!properties.enabled() || imageUrls == null || imageUrls.isEmpty()) {
            return ModerationDecision.allow();
        }
        return execute(new ModerationRequest(contentId, "PHOTO", null, imageUrls, authorId));
    }

    public void submitReport(String contentId, String text, String authorId) {
        if (!properties.enabled()) {
            return;
        }
        execute(new ModerationRequest(contentId, "REPORT", text, List.of(), authorId));
    }

    public void requireAllowedText(String contentId, String text, String authorId) {
        if (moderateText(contentId, text, authorId).blocked()) {
            throw new ContentBlockedException("This message was blocked by moderation.");
        }
    }

    public void requireAllowedImages(String contentId, List<String> imageUrls, String authorId) {
        if (moderateImages(contentId, imageUrls, authorId).blocked()) {
            throw new ContentBlockedException("This photo was blocked by moderation.");
        }
    }

    private ModerationDecision execute(ModerationRequest request) {
        try {
            ModerationResponse response = client.post()
                    .uri("/internal/v1/moderations")
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ModerationResponse.class);
            if (response == null || response.decision() == null) {
                return ModerationDecision.allow();
            }
            return new ModerationDecision(response.decision(), response.reason());
        } catch (RestClientException error) {
            log.warn("Moderation unavailable for {}: {}", request.contentId(), error.getMessage());
            return ModerationDecision.allow();
        }
    }

    private String basicAuth() {
        String username = properties.service().username() == null ? "" : properties.service().username();
        String password = properties.service().password() == null ? "" : properties.service().password();
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
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
