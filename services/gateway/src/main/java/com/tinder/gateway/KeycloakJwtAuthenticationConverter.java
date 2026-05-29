package com.tinder.gateway;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private final Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter =
        new KeycloakGrantedAuthoritiesConverter();
    private final Cache<String, CachedAuthentication> authenticationCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        CachedAuthentication cached = authenticationCache.getIfPresent(jwt.getTokenValue());
        if (cached != null && cached.isUsable()) {
            return Mono.just(new JwtAuthenticationToken(jwt, cached.authorities(), cached.principalName()));
        }

        Collection<GrantedAuthority> authorities = List.copyOf(jwtGrantedAuthoritiesConverter.convert(jwt));
        String principalName = jwt.getClaimAsString("preferred_username");
        if (principalName == null) {
            principalName = jwt.getSubject();
        }

        CachedAuthentication resolved = new CachedAuthentication(authorities, principalName, jwt.getExpiresAt());
        if (resolved.isUsable()) {
            authenticationCache.put(jwt.getTokenValue(), resolved);
        }
        return Mono.just(new JwtAuthenticationToken(jwt, authorities, principalName));
    }

    private static class KeycloakGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Collection<GrantedAuthority> grantedAuthorities = new ArrayList<>();

            // Extract realm_access roles
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                Collection<String> roles = (Collection<String>) realmAccess.get("roles");
                grantedAuthorities.addAll(roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .collect(Collectors.toList()));
            }

            // Extract resource_access roles (optional)
            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            if (resourceAccess != null) {
                resourceAccess.forEach((resource, resourceClaims) -> {
                    if (resourceClaims instanceof Map) {
                        Map<String, Object> resourceClaimsMap = (Map<String, Object>) resourceClaims;
                        if (resourceClaimsMap.containsKey("roles")) {
                            Collection<String> roles = (Collection<String>) resourceClaimsMap.get("roles");
                            grantedAuthorities.addAll(roles.stream()
                                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                                    .collect(Collectors.toList()));
                        }
                    }
                });
            }

            return grantedAuthorities;
        }
    }

    private record CachedAuthentication(
            Collection<GrantedAuthority> authorities,
            String principalName,
            Instant expiresAt
    ) {
        boolean isUsable() {
            return expiresAt == null || expiresAt.isAfter(Instant.now());
        }
    }
}
