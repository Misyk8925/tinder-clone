package com.tinder.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

@Component
public class GatewayJwtSubjectResolver {

    private final ObjectMapper objectMapper;

    public GatewayJwtSubjectResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<String> resolve(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String[] parts = authorization.substring("Bearer ".length()).split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }

        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode sub = objectMapper.readTree(new String(payload, StandardCharsets.UTF_8)).get("sub");
            if (sub == null || sub.asText().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(sub.asText());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
