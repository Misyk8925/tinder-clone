package com.tinder.profiles.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that enriches the MDC (Mapped Diagnostic Context) with
 * per-request tracing keys so every log line carries:
 *
 *  - traceId       — set automatically by Micrometer Tracing (Brave)
 *  - spanId        — set automatically by Micrometer Tracing (Brave)
 *  - userId        — Keycloak subject from JWT (set by Spring Security)
 *  - correlationId — X-Correlation-ID header value (session-level)
 *  - httpMethod    — GET / POST / PATCH / DELETE / …
 *  - httpUrl       — request URI, e.g. /api/v1/profiles/
 *  - httpQuery     — query string, e.g. offset=0&limit=20  (empty if none)
 *  - httpStatus    — response status code, written after the request completes
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)  // run early, but after Spring Security
public class UserContextMdcFilter extends OncePerRequestFilter {

    /** Header name that the API-Gateway (or client) may set for session-level correlation. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    /** Header name exposed back to the caller so they can reference the trace in support tickets. */
    public static final String TRACE_ID_RESPONSE_HEADER = "X-Trace-ID";

    private static final String MDC_USER_ID        = "userId";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_HTTP_METHOD    = "httpMethod";
    private static final String MDC_HTTP_URL       = "httpUrl";
    private static final String MDC_HTTP_QUERY     = "httpQuery";
    private static final String MDC_HTTP_STATUS    = "httpStatus";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            // --- correlationId: reuse from header or mint a new one ---
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            MDC.put(MDC_CORRELATION_ID, correlationId);

            // Expose the correlationId back in the response so the client can log it
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            // --- HTTP context ---
            String method = request.getMethod();
            String uri    = request.getRequestURI();
            String query  = request.getQueryString() != null ? request.getQueryString() : "";
            MDC.put(MDC_HTTP_METHOD, method);
            MDC.put(MDC_HTTP_URL,    uri);
            MDC.put(MDC_HTTP_QUERY,  query);

            // --- userId: extracted from the JWT principal (set by Spring Security) ---
            String userId = resolveUserId();
            if (userId != null) {
                MDC.put(MDC_USER_ID, userId);
            }

            filterChain.doFilter(request, response);

            // --- status + traceId are available only after the chain completes ---
            int    status  = response.getStatus();
            String traceId = MDC.get("traceId");

            MDC.put(MDC_HTTP_STATUS, String.valueOf(status));
            if (traceId != null) {
                response.setHeader(TRACE_ID_RESPONSE_HEADER, traceId);
            }

            // Single structured access-log line — easy to filter in Kibana
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("{} {} {} {} {}ms traceId={} correlationId={}",
                    method,
                    uri,
                    query.isEmpty() ? "" : "?" + query,
                    status,
                    durationMs,
                    traceId,
                    MDC.get(MDC_CORRELATION_ID));

        } finally {
            // Always clean up to prevent MDC leaking into thread-pool neighbours
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_HTTP_METHOD);
            MDC.remove(MDC_HTTP_URL);
            MDC.remove(MDC_HTTP_QUERY);
            MDC.remove(MDC_HTTP_STATUS);
        }
    }

    /**
     * Tries to resolve the authenticated user's subject (Keycloak userId) from
     * the current Spring Security context.  Returns {@code null} when the
     * request is unauthenticated (e.g. public endpoints).
     */
    private String resolveUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                return jwt.getSubject();
            }
        } catch (Exception ignored) {
            // Never fail the request just because we couldn't read the userId
        }
        return null;
    }
}
