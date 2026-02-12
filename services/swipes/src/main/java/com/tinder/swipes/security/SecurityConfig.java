package com.tinder.swipes.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return new JwtAuthConverter();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            StringRedisTemplate stringRedisTemplate
    ) {
        NimbusJwtDecoder nimbusJwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        return new CachedJwtDecoder(nimbusJwtDecoder, stringRedisTemplate);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity httpSecurity,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
            JwtDecoder jwtDecoder
    ) throws Exception {

        return httpSecurity
                .authorizeHttpRequests(auth -> auth
                        .anyRequest()
                        .authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers(
                "/internal/**"
        );
    }
}