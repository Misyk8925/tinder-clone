package com.tinder.profiles.config.security;

import com.tinder.profiles.config.props.ProfileCacheProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@EnableConfigurationProperties(OAuth2ResourceServerProperties.class)
public class JwtDecoderConfig {

    /**
     * The JWK set URI is Spring Boot's own resource-server property, so it is read
     * from {@link OAuth2ResourceServerProperties} rather than duplicated here.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            ProfileCacheProperties cacheProperties
    ) {
        JwtDecoder delegate = NimbusJwtDecoder
                .withJwkSetUri(resourceServerProperties.getJwt().getJwkSetUri())
                .build();
        return new CachingJwtDecoder(delegate, cacheProperties);
    }
}
