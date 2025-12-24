package com.tinder.gateway;

import reactor.core.publisher.Mono;

/**
 * Helper class for determining user role reactively from SecurityService
 */
public class RoleResolver {

    /**
     * Determines user role reactively in priority order: admin > premium > basic > anon
     *
     * @param securityService the security service to check roles
     * @return Mono<String> with role name: "admin", "premium", "basic", or "anon"
     */
    public static Mono<String> resolveRole(SecurityService securityService) {
        return securityService.isAdmin()
                .flatMap(isAdmin -> {
                    if (isAdmin) {
                        return Mono.just("admin");
                    }
                    return securityService.isPremiumUser()
                            .flatMap(isPremium -> {
                                if (isPremium) {
                                    return Mono.just("premium");
                                }
                                return securityService.isBasicUser()
                                        .map(isBasic -> isBasic ? "basic" : "anon");
                            });
                });
    }
}

