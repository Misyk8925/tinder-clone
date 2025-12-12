package com.tinder.profiles.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Security Service для работы с JWT токенами и проверки прав доступа.
 * Используется в Bucket4j rate limiting и в бизнес-логике приложения.
 *
 * Интегрируется с существующими JwtAuthConverter и SecurityConfig.
 */
@Slf4j
@Service
public class SecurityService {

    // ========== Базовые проверки аутентификации ==========

    /**
     * Проверяет, авторизован ли текущий пользователь.
     *
     * @return true если пользователь авторизован через JWT токен
     */
    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    /**
     * Проверяет, является ли текущий пользователь анонимным.
     *
     * @return true если пользователь не авторизован
     */
    public boolean isAnonymous() {
        return !isAuthenticated();
    }

    // ========== Извлечение данных из JWT ==========

    /**
     * Получает username текущего пользователя из JWT токена.
     * Использует 'sub' claim из токена (как настроено в JwtAuthConverter).
     *
     * Использование в Bucket4j:
     * cache-key: "@securityService.username()"
     *
     * @return username или null если не авторизован
     */
    public String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            // Ваш JwtAuthConverter использует getPrincipalClaimName который возвращает 'sub'
            return jwtAuth.getName(); // Это будет значение из 'sub' claim
        }

        return auth != null && isAuthenticated() ? auth.getName() : null;
    }

    /**
     * Получает user ID из JWT токена.
     * В Keycloak 'sub' claim обычно содержит UUID пользователя.
     *
     * Использование в Bucket4j:
     * cache-key: "@securityService.getUserId().toString()"
     *
     * @return UUID пользователя или null если не авторизован или sub не является UUID
     */
    public UUID getUserId() {
        String sub = getSubject();
        if (sub == null) {
            return null;
        }

        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            log.warn("JWT 'sub' claim is not a valid UUID: {}", sub);
            return null;
        }
    }

    /**
     * Получает 'sub' claim из JWT токена.
     *
     * @return subject или null
     */
    public String getSubject() {
        Jwt jwt = getJwt();
        return jwt != null ? jwt.getSubject() : null;
    }

    /**
     * Получает email из JWT токена (если присутствует в claims).
     *
     * @return email или null
     */
    public String getEmail() {
        Jwt jwt = getJwt();
        return jwt != null ? jwt.getClaimAsString("email") : null;
    }

    /**
     * Получает preferred_username из JWT токена (Keycloak claim).
     * Обычно это более читаемое имя пользователя чем sub.
     *
     * @return preferred username или null
     */
    public String getPreferredUsername() {
        Jwt jwt = getJwt();
        return jwt != null ? jwt.getClaimAsString("preferred_username") : null;
    }

    /**
     * Получает любой claim из JWT токена.
     *
     * @param claimName название claim
     * @return значение claim или null
     */
    public String getClaim(String claimName) {
        Jwt jwt = getJwt();
        return jwt != null ? jwt.getClaimAsString(claimName) : null;
    }

    // ========== Проверка ролей и прав доступа ==========

    /**
     * Проверяет наличие конкретной роли у пользователя.
     *
     * Ваш JwtAuthConverter добавляет префикс "ROLE_" к ролям из Keycloak,
     * поэтому роль "USER" в Keycloak становится "ROLE_USER" в Spring Security.
     *
     * Использование в Bucket4j:
     * execute-condition: "@securityService.hasRole('ADMIN')"
     *
     * @param role название роли БЕЗ префикса ROLE_ (например "ADMIN", "USER")
     * @return true если у пользователя есть эта роль
     */
    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !isAuthenticated()) {
            return false;
        }

        // JwtAuthConverter добавляет префикс "ROLE_" к ролям
        String roleWithPrefix = "ROLE_" + role.toUpperCase();

        return auth.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(roleWithPrefix));
    }

    /**
     * Проверяет наличие любой из указанных ролей.
     *
     * @param roles список ролей
     * @return true если есть хотя бы одна из ролей
     */
    public boolean hasAnyRole(String... roles) {
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет наличие всех указанных ролей.
     *
     * @param roles список ролей
     * @return true если есть все роли
     */
    public boolean hasAllRoles(String... roles) {
        for (String role : roles) {
            if (!hasRole(role)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Проверяет, является ли пользователь администратором.
     *
     * Использование в Bucket4j:
     * execute-condition: "@securityService.isAdmin()"
     *
     * @return true если есть роль ADMIN
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * Получает все роли текущего пользователя.
     *
     * @return коллекция ролей с префиксом ROLE_
     */
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getAuthorities() : null;
    }

    // ========== Специфичные проверки для Dating App ==========

    /**
     * Проверяет, является ли пользователь премиум-подписчиком.
     * Эта информация должна быть добавлена в JWT токен в Keycloak
     * как custom claim (например "subscription_type": "PREMIUM").
     *
     * Использование в Bucket4j:
     * execute-condition: "@securityService.isPremium()"
     *
     * @return true если в JWT есть claim указывающий на премиум
     */
    public boolean isPremium() {
        Jwt jwt = getJwt();
        if (jwt == null) {
            return false;
        }

        // Проверяем custom claim для subscription
        String subscriptionType = jwt.getClaimAsString("subscription_type");
        if ("PREMIUM".equalsIgnoreCase(subscriptionType)) {
            return true;
        }

        // Альтернатива: проверка через роль
        return hasRole("PREMIUM");
    }

    /**
     * Проверяет, является ли пользователь обычным (не премиум).
     *
     * Использование в Bucket4j:
     * execute-condition: "@securityService.isFree()"
     *
     * @return true если авторизован но не премиум
     */
    public boolean isFree() {
        return isAuthenticated() && !isPremium();
    }

    /**
     * Проверяет, заблокирован ли пользователь.
     * Информация о блокировке должна быть в JWT claim.
     *
     * Использование в Bucket4j:
     * execute-condition: "@securityService.isBlocked()"
     *
     * @return true если пользователь заблокирован
     */
    public boolean isBlocked() {
        Jwt jwt = getJwt();
        if (jwt == null) {
            return false;
        }

        // Проверяем custom claim для блокировки
        Boolean blocked = jwt.getClaim("is_blocked");
        return Boolean.TRUE.equals(blocked);
    }

    /**
     * Проверяет, верифицирован ли email пользователя.
     * Keycloak по умолчанию добавляет claim "email_verified".
     *
     * @return true если email верифицирован
     */
    public boolean isEmailVerified() {
        Jwt jwt = getJwt();
        if (jwt == null) {
            return false;
        }

        Boolean emailVerified = jwt.getClaim("email_verified");
        return Boolean.TRUE.equals(emailVerified);
    }

    // ========== Проверки токена ==========

    /**
     * Проверяет, не истёк ли JWT токен.
     *
     * @return true если токен действителен
     */
    public boolean isTokenValid() {
        Jwt jwt = getJwt();
        if (jwt == null) {
            return false;
        }

        Instant expiresAt = jwt.getExpiresAt();
        return expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    /**
     * Получает время истечения токена.
     *
     * @return Instant когда токен истекает, или null
     */
    public Instant getTokenExpiration() {
        Jwt jwt = getJwt();
        return jwt != null ? jwt.getExpiresAt() : null;
    }

    /**
     * Получает время создания токена.
     *
     * @return Instant когда токен был создан, или null
     */
    public Instant getTokenIssuedAt() {
        Jwt jwt = getJwt();
        return jwt != null ? jwt.getIssuedAt() : null;
    }

    // ========== Внутренние методы ==========

    /**
     * Получает полный JWT токен из SecurityContext.
     *
     * @return Jwt токен или null если не авторизован
     */
    private Jwt getJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }

    // ========== Utility методы ==========

    /**
     * Получает текущую Authentication из SecurityContext.
     * Полезно для передачи в другие методы Spring Security.
     *
     * @return Authentication или null
     */
    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Выводит отладочную информацию о текущем пользователе.
     * Полезно для debugging и логирования.
     *
     * @return строка с информацией о пользователе
     */
    public String getDebugInfo() {
        if (!isAuthenticated()) {
            return "Anonymous User";
        }

        StringBuilder info = new StringBuilder();
        info.append("User: ").append(username()).append("\n");
        info.append("Subject: ").append(getSubject()).append("\n");
        info.append("Email: ").append(getEmail()).append("\n");
        info.append("Preferred Username: ").append(getPreferredUsername()).append("\n");
        info.append("Roles: ");

        Collection<? extends GrantedAuthority> authorities = getAuthorities();
        if (authorities != null) {
            authorities.forEach(auth -> info.append(auth.getAuthority()).append(" "));
        }

        info.append("\nToken Valid: ").append(isTokenValid());
        info.append("\nToken Expires At: ").append(getTokenExpiration());
        info.append("\nIs Premium: ").append(isPremium());
        info.append("\nIs Admin: ").append(isAdmin());

        return info.toString();
    }
}