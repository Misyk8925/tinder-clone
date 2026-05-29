package com.tinder.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.time.Duration;

@Configuration
public class JwtDecoderConfig {

    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${gateway.jwt-cache.ttl:5m}") Duration ttl,
            @Value("${gateway.jwt-cache.max-size:10000}") long maxSize,
            @Value("${gateway.jwt-cache.max-token-length:8192}") int maxTokenLength
    ) {
        ReactiveJwtDecoder delegate = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        return new CachingReactiveJwtDecoder(delegate, ttl, maxSize, maxTokenLength);
    }
}
