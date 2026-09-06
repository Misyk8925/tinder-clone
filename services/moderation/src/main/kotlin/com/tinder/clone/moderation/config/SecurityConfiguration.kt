package com.tinder.clone.moderation.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfiguration {
    @Bean
    fun userDetailsService(properties: ModerationSecurityProperties): UserDetailsService {
        val users = properties.users
            .filter { it.username.isNotBlank() }
            .map { configured ->
                require(configured.passwordHash.startsWith("{bcrypt}\$2") || configured.passwordHash.startsWith("\$2")) {
                    "Password for ${configured.username} must be a BCrypt hash"
                }
                User.withUsername(configured.username)
                    .password("{bcrypt}${configured.passwordHash.removePrefix("{bcrypt}")}")
                    .roles(*configured.roles.toTypedArray())
                    .build()
            }
        return InMemoryUserDetailsManager(users)
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers(
                        "/internal/v1/policies/**",
                        "/internal/v1/policy-activations/**",
                        "/internal/v1/policy-previews/**"
                    ).hasRole("POLICY_ADMIN")
                    .requestMatchers("/internal/v1/review-tasks/**").hasAnyRole("MODERATOR", "POLICY_ADMIN")
                    .requestMatchers("/internal/v1/**", "/admin/**").hasAnyRole("VIEWER", "MODERATOR", "POLICY_ADMIN")
                    .anyRequest().authenticated()
            }
            .csrf { csrf -> csrf.ignoringRequestMatchers("/internal/**") }
            .httpBasic { basic ->
                basic.authenticationEntryPoint { _, response, _ ->
                    response.status = 401
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.write("""{"code":"UNAUTHENTICATED","message":"Authentication required","retryable":false}""")
                }
            }
            .exceptionHandling { handling ->
                handling.accessDeniedHandler { _, response, _ ->
                    response.status = 403
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.write("""{"code":"FORBIDDEN","message":"Insufficient role","retryable":false}""")
                }
            }
            .formLogin { form -> form.permitAll() }
        return http.build()
    }
}
