package com.tinder.profiles.security;

import com.tinder.profiles.profile.cache.DeckPageCacheService;
import com.tinder.profiles.profile.cache.ProfileCacheProperties;
import com.tinder.profiles.profile.cache.ProfileIdentityCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DeckHotPathFilter extends OncePerRequestFilter {

    private static final String DECK_PATH = "/api/v1/profiles/deck";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final DeckHotPathTokenCache tokenCache;
    private final DeckPageCacheService deckPageCacheService;
    private final ProfileCacheProperties properties;
    private final InternalAuthVerifier internalAuthVerifier;
    private final ProfileIdentityCacheService profileIdentityCacheService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isDeckGet(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<UUID> profileId = resolveProfileId(request);
        if (profileId.isEmpty()) {
            if (isOversizedBearerToken(request)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        PageRequest page = parsePageRequest(request);
        if (page == null) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] cachedJson = deckPageCacheService.getBytes(profileId.get(), page.offset(), page.limit());
        if (cachedJson == null) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentLength(cachedJson.length);
        response.getOutputStream().write(cachedJson);
    }

    private Optional<UUID> resolveProfileId(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (token != null && token.length() <= properties.getDeckHotPath().getMaxTokenLength()) {
            Optional<UUID> profileId = tokenCache.getProfileId(token);
            if (profileId.isPresent()) {
                return profileId;
            }
        }

        String internalAuth = request.getHeader(InternalAuthVerifier.HEADER_NAME);
        if (!internalAuthVerifier.isValid(internalAuth)) {
            return Optional.empty();
        }

        String userSubject = request.getHeader(InternalAuthVerifier.USER_SUBJECT_HEADER);
        UUID profileId = profileIdentityCacheService.getCachedProfileId(userSubject);
        return profileId == null ? Optional.empty() : Optional.of(profileId);
    }

    private boolean isDeckGet(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && DECK_PATH.equals(request.getRequestURI());
    }

    private boolean isOversizedBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null
                && authorization.startsWith(BEARER_PREFIX)
                && authorization.length() > BEARER_PREFIX.length() + properties.getDeckHotPath().getMaxTokenLength();
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        if (authorization.length() > BEARER_PREFIX.length() + properties.getDeckHotPath().getMaxTokenLength()) {
            return authorization;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private PageRequest parsePageRequest(HttpServletRequest request) {
        Integer offset = parseInteger(request.getParameter("offset"), DEFAULT_OFFSET);
        Integer limit = parseInteger(request.getParameter("limit"), DEFAULT_LIMIT);
        if (offset == null || limit == null || offset < 0 || limit < 1 || limit > MAX_LIMIT) {
            return null;
        }

        return new PageRequest(offset, limit);
    }

    private Integer parseInteger(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record PageRequest(int offset, int limit) {}
}
