package com.tinder.clone.moderation.infrastructure.http

import com.tinder.clone.moderation.application.service.ModerationDecisionStore
import com.tinder.clone.moderation.application.service.ReviewAction
import com.tinder.clone.moderation.application.service.ReviewLifecycleException
import com.tinder.clone.moderation.application.service.ReviewStatus
import com.tinder.clone.moderation.application.service.ReviewTask
import com.tinder.clone.moderation.application.ports.MutationAuditPort
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID

data class ReviewResolutionRequestDto(
    val action: ReviewAction,
    @field:Size(max = 2000) val note: String? = null
)

@RestController
@RequestMapping("/internal/v1/review-tasks")
class ReviewController(
    private val store: ModerationDecisionStore,
    private val audit: MutationAuditPort
) {
    @GetMapping
    fun list(@RequestParam(required = false) status: ReviewStatus?): Map<String, Any?> =
        mapOf("items" to store.listReviews(status), "nextCursor" to null)

    @GetMapping("/{reviewTaskId}")
    fun get(@PathVariable reviewTaskId: UUID): ReviewTask = store.getReview(reviewTaskId)
        ?: throw ResourceNotFoundException("Review task not found")

    @PutMapping("/{reviewTaskId}/resolution")
    fun resolve(
        @PathVariable reviewTaskId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @Valid @RequestBody request: ReviewResolutionRequestDto,
        principal: Principal
    ): ReviewTask = try {
        store.resolveReview(reviewTaskId, parseVersion(ifMatch), request.action, request.note, principal.name)
    } catch (error: RuntimeException) {
        runCatching {
            audit.recordFailure(
                principal.name,
                "RESOLVE_REVIEW",
                "REVIEW_TASK",
                reviewTaskId.toString(),
                if (error is ReviewLifecycleException) error.code else "FAILED"
            )
        }
        throw error
    }

    private fun parseVersion(header: String): Long = header.removeSurrounding("\"").toLongOrNull()
        ?: throw IllegalArgumentException("If-Match must be a quoted numeric version")
}
