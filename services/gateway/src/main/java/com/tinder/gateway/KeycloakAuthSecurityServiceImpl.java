package com.tinder.gateway;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KeycloakAuthSecurityServiceImpl implements SecurityService{

    @Override
    public Mono<Boolean> isBasicUser() {
        return hasRole("USER_BASIC");
    }

    @Override
    public Mono<Boolean> isPremiumUser() {
        return hasRole("USER_PREMIUM");
    }

    @Override
    public Mono<Boolean> isAdmin() {
        return hasRole("ADMIN");
    }

    @Override
    public Mono<Boolean> isBlocked() {
        return hasRole("BLOCKED")
                .flatMap(blocked -> {
                    if (!blocked) {
                        return hasRole("USER_BASIC").map(basic -> !basic);
                    }
                    return Mono.just(true);
                });
    }

    private Mono<Boolean> hasRole(String role) {
        if (role == null || role.isEmpty()) {
            log.warn("Role is null or empty");
            return Mono.just(false);
        }

        log.info("Checking role: {}", role);

        // Use reactive security context for WebFlux
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> {
                    log.info("SecurityContext found: {}", securityContext);
                    return securityContext.getAuthentication();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("No SecurityContext found");
                    return Mono.empty();
                }))
                .map(auth -> {
                    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                        log.warn("No authenticated user found");
                        return false;
                    }

                    log.info("Found authenticated user: {} with authorities: {}", auth.getName(), auth.getAuthorities());

                    String roleWithPrefix = "ROLE_" + role.toUpperCase();

                    boolean hasRole = auth.getAuthorities() != null && auth.getAuthorities().stream()
                            .anyMatch(authority -> authority.getAuthority().equals(roleWithPrefix));

                    log.info("User {} has role {}: {}", auth.getName(), roleWithPrefix, hasRole);

                    return hasRole;
                })
                .defaultIfEmpty(false);
    }
}
