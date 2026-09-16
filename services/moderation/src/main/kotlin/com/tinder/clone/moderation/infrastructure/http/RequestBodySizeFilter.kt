package com.tinder.clone.moderation.infrastructure.http

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class RequestBodySizeFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (
            request.requestURI.startsWith("/internal/v1/") &&
            request.contentLengthLong > MAX_REQUEST_BYTES
        ) {
            response.status = HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write(
                """{"code":"PAYLOAD_TOO_LARGE","message":"Request body exceeds 1 MiB","retryable":false}"""
            )
            return
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val MAX_REQUEST_BYTES = 1024L * 1024L
    }
}
