package com.tinder.clone.moderation.infrastructure.http

import com.tinder.clone.moderation.application.service.ModerationExecutionOutcome
import com.tinder.clone.moderation.application.service.ModerationExecutionService
import com.tinder.clone.moderation.application.service.ValidationReason
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/v1/moderations")
class ModerationController(private val executions: ModerationExecutionService) {
    @PostMapping
    fun moderate(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: ModerationRequestDto
    ): ResponseEntity<*> {
        require(idempotencyKey.length in 8..128) { "Idempotency-Key length must be between 8 and 128" }
        return when (val result = executions.execute(idempotencyKey, request)) {
            is ModerationExecutionOutcome.Evaluated -> ResponseEntity.ok(result.response)
            is ModerationExecutionOutcome.Invalid -> {
                val status = if (result.reason == ValidationReason.PAYLOAD_TOO_LARGE) HttpStatus.CONTENT_TOO_LARGE else HttpStatus.BAD_REQUEST
                ResponseEntity.status(status).body(ApiErrorDto(result.reason.name, "Moderation payload is invalid", false))
            }
            is ModerationExecutionOutcome.Throttled -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", result.retryAfterSeconds.toString())
                .body(ApiErrorDto("RATE_LIMITED", "Retry later", true))
        }
    }

    @GetMapping
    fun list(): Map<String, Any?> = mapOf("items" to executions.list(), "nextCursor" to null)

    @GetMapping("/{decisionId}")
    fun get(@PathVariable decisionId: UUID): ModerationResponseDto = executions.get(decisionId)
        ?: throw ResourceNotFoundException("Moderation decision not found")
}
