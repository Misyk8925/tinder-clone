package com.tinder.clone.moderation.infrastructure.http

import com.tinder.clone.moderation.infrastructure.provider.ProviderException
import com.tinder.clone.moderation.application.policy.PolicyLifecycleException
import com.tinder.clone.moderation.application.service.IdempotencyConflictException
import com.tinder.clone.moderation.infrastructure.persistence.DurableStorageException
import com.tinder.clone.moderation.application.service.ReviewLifecycleException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(IdempotencyConflictException::class)
    fun idempotencyConflict(): ResponseEntity<ApiErrorDto> = ResponseEntity.status(HttpStatus.CONFLICT).body(
        ApiErrorDto("IDEMPOTENCY_CONFLICT", "Idempotency key payload differs", false)
    )

    @ExceptionHandler(ResourceNotFoundException::class)
    fun notFound(error: ResourceNotFoundException): ResponseEntity<ApiErrorDto> = ResponseEntity.status(HttpStatus.NOT_FOUND).body(
        ApiErrorDto("NOT_FOUND", error.message ?: "Resource not found", false)
    )
    @ExceptionHandler(PolicyLifecycleException::class)
    fun policy(error: PolicyLifecycleException): ResponseEntity<ApiErrorDto> {
        val status = when (error.code) {
            "NOT_FOUND" -> HttpStatus.NOT_FOUND
            "POLICY_VERSION_EXISTS", "VERSION_CONFLICT" -> HttpStatus.CONFLICT
            "POLICY_INVALID", "POLICY_NOT_PUBLISHED", "NO_PREVIOUS_POLICY", "PUBLISHED_POLICY_IMMUTABLE" ->
                HttpStatus.UNPROCESSABLE_CONTENT
            else -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity.status(status).body(
            ApiErrorDto(error.code, error.message ?: error.code, retryable = error.code == "VERSION_CONFLICT")
        )
    }

    @ExceptionHandler(ReviewLifecycleException::class)
    fun review(error: ReviewLifecycleException): ResponseEntity<ApiErrorDto> {
        val status = when (error.code) {
            "NOT_FOUND" -> HttpStatus.NOT_FOUND
            "VERSION_CONFLICT" -> HttpStatus.CONFLICT
            "REVIEW_ALREADY_RESOLVED" -> HttpStatus.UNPROCESSABLE_CONTENT
            else -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity.status(status).body(
            ApiErrorDto(error.code, error.message ?: error.code, retryable = error.code == "VERSION_CONFLICT")
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun malformedBody(): ResponseEntity<ApiErrorDto> = ResponseEntity.badRequest().body(
        ApiErrorDto("INVALID_PAYLOAD", "Request body is malformed", retryable = false)
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidArgument(error: MethodArgumentNotValidException): ResponseEntity<ApiErrorDto> =
        ResponseEntity.badRequest().body(
            ApiErrorDto(
                code = "INVALID_PAYLOAD",
                message = "Request validation failed",
                retryable = false,
                fieldErrors = error.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
            )
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidArgument(error: IllegalArgumentException): ResponseEntity<ApiErrorDto> =
        ResponseEntity.badRequest().body(
            ApiErrorDto("INVALID_PAYLOAD", error.message ?: "Request validation failed", retryable = false)
        )

    @ExceptionHandler(ProviderException::class)
    fun providerUnavailable(error: ProviderException): ResponseEntity<ApiErrorDto> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "1")
            .body(ApiErrorDto("SERVICE_UNAVAILABLE", "${error.provider} provider is unavailable", retryable = true))

    @ExceptionHandler(DurableStorageException::class)
    fun storageUnavailable(): ResponseEntity<ApiErrorDto> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "1")
            .body(ApiErrorDto("SERVICE_UNAVAILABLE", "Durable storage is unavailable", retryable = true))
}

class ResourceNotFoundException(message: String) : RuntimeException(message)
