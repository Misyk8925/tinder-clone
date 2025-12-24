package com.tinder.gateway;

import reactor.core.publisher.Mono;

public interface SecurityService {

    Mono<Boolean> isBasicUser();
    Mono<Boolean> isPremiumUser();
    Mono<Boolean> isAdmin();
    Mono<Boolean> isBlocked();
}


