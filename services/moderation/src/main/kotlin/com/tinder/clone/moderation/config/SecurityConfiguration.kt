package com.tinder.clone.moderation.config

import com.tinder.clone.moderation.application.security.InMemoryLoginAttemptStore
import com.tinder.clone.moderation.application.security.LoginAttemptService
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.LinkedHashMap

@Configuration
class SecurityConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun loginAttemptStore() = InMemoryLoginAttemptStore()

    @Bean
    fun loginAttemptService(
        store: com.tinder.clone.moderation.application.security.LoginAttemptStore,
        clock: Clock,
        properties: ModerationSecurityProperties
    ) = LoginAttemptService(store, clock, properties.lockoutThreshold, properties.lockoutDuration)

    @Bean
    fun userDetailsService(
        properties: ModerationSecurityProperties,
        attempts: LoginAttemptService
    ): UserDetailsService {
        val users = properties.users
            .filter { it.username.isNotBlank() }
            .map { configured ->
                require(configured.passwordHash.startsWith("{bcrypt}\$2") || configured.passwordHash.startsWith("\$2")) {
                    "Password for ${configured.username} must be a BCrypt hash"
                }
                User.withUsername(configured.username)
                    .password("{bcrypt}${configured.passwordHash.removePrefix("{bcrypt}")}")
                    .roles(*configured.roles.toTypedArray())
                    .accountLocked(attempts.isLocked(configured.username))
                    .build()
            }
        return UserDetailsService { username ->
            val loaded = InMemoryUserDetailsManager(users).loadUserByUsername(username)
            User.withUserDetails(loaded)
                .accountLocked(attempts.isLocked(username))
                .build()
        }
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val jsonEntryPoint = AuthenticationEntryPoint { _, response, _ ->
            response.status = 401
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"code":"UNAUTHENTICATED","message":"Authentication required","retryable":false}""")
        }
        val entryPoints = LinkedHashMap<RequestMatcher, AuthenticationEntryPoint>()
        entryPoints[RequestMatcher { request -> request.requestURI.startsWith("/internal/") }] = jsonEntryPoint
        val entryPoint = DelegatingAuthenticationEntryPoint(entryPoints).apply {
            setDefaultEntryPoint(LoginUrlAuthenticationEntryPoint("/login"))
        }
        http
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/login", "/error").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
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
            .exceptionHandling { handling ->
                handling.authenticationEntryPoint(entryPoint)
                handling.accessDeniedHandler { _, response, _ ->
                    response.status = 403
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.write("""{"code":"FORBIDDEN","message":"Insufficient role","retryable":false}""")
                }
            }
            .httpBasic { }
            .formLogin { form -> form.permitAll() }
        return http.build()
    }
}

@Component
class LoginAttemptListeners(private val attempts: LoginAttemptService) {
    @EventListener
    fun onFailure(event: AbstractAuthenticationFailureEvent) {
        val name = event.authentication?.name ?: return
        attempts.recordFailure(name)
    }

    @EventListener
    fun onSuccess(event: AuthenticationSuccessEvent) {
        attempts.recordSuccess(event.authentication.name)
    }
}
