package com.tinder.clone.moderation.infrastructure.http

import com.tinder.clone.moderation.application.policy.RuntimePolicyRegistry
import com.tinder.clone.moderation.application.ports.MutationAuditPort
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.security.Principal

@RestController
@RequestMapping("/internal/v1")
class PolicyController(
    private val policies: RuntimePolicyRegistry,
    private val audit: MutationAuditPort
) {
    @PostMapping("/policies")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: PolicyDraftRequestDto, principal: Principal): PolicyVersionDto =
        auditFailure(principal.name, "CREATE_POLICY", "POLICY", request.version) {
            policies.create(request.version, request.description, request.toDefinitions(), principal.name).toDto()
        }

    @GetMapping("/policies")
    fun list(): Map<String, Any?> = mapOf("items" to policies.list().map { it.toDto() }, "nextCursor" to null)

    @GetMapping("/policies/{version}")
    fun get(@PathVariable version: String): PolicyVersionDto = policies.get(version).toDto()

    @PutMapping("/policies/{version}")
    fun replaceDraft(
        @PathVariable version: String,
        @RequestHeader("If-Match") ifMatch: String,
        @Valid @RequestBody request: PolicyDraftRequestDto,
        principal: Principal
    ): PolicyVersionDto {
        return auditFailure(principal.name, "UPDATE_POLICY", "POLICY", version) {
            require(version == request.version) { "Path and body policy versions must match" }
            policies.replaceDraft(
                version,
                parseVersion(ifMatch),
                request.description,
                request.toDefinitions(),
                principal.name
            ).toDto()
        }
    }

    @PostMapping("/policies/{version}/validation")
    fun validate(@PathVariable version: String): PolicyValidationDto {
        val errors = policies.validation(version)
        return PolicyValidationDto(errors.isEmpty(), errors)
    }

    @PostMapping("/policies/{version}/publication")
    fun publish(
        @PathVariable version: String,
        @RequestHeader("If-Match") ifMatch: String,
        principal: Principal
    ): PolicyVersionDto = auditFailure(principal.name, "PUBLISH_POLICY", "POLICY", version) {
        policies.publish(version, parseVersion(ifMatch), principal.name).toDto()
    }

    @PutMapping("/policies/{version}/activation")
    fun activate(
        @PathVariable version: String,
        @RequestBody scope: PolicyScopeDto,
        principal: Principal
    ): PolicyActivationDto = auditFailure(principal.name, "ACTIVATE_POLICY", "POLICY", version) {
        policies.activate(version, scope.contentType, scope.locale, principal.name).toDto()
    }

    @PostMapping("/policy-activations/{activationId}/rollback")
    fun rollback(
        @PathVariable activationId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        principal: Principal
    ): PolicyActivationDto = auditFailure(
        principal.name,
        "ROLLBACK_POLICY",
        "POLICY_ACTIVATION",
        activationId.toString()
    ) {
        policies.rollback(activationId, parseVersion(ifMatch), principal.name).toDto()
    }

    @PostMapping("/policy-previews")
    fun preview(@RequestBody request: PolicyPreviewRequestDto): Map<String, Any?> {
        val errors = policies.validation(request.policyVersion)
        if (errors.isNotEmpty()) throw com.tinder.clone.moderation.application.policy.PolicyLifecycleException(
            "POLICY_INVALID", errors.joinToString("; ")
        )
        return mapOf("decision" to "HOLD", "reason" to "PREVIEW_REQUIRES_CLASSIFIER_EVIDENCE", "persisted" to false)
    }

    private fun parseVersion(header: String): Long = header.removeSurrounding("\"").toLongOrNull()
        ?: throw IllegalArgumentException("If-Match must be a quoted numeric version")

    private fun <T> auditFailure(
        actor: String,
        action: String,
        targetType: String,
        targetId: String,
        block: () -> T
    ): T = try {
        block()
    } catch (error: RuntimeException) {
        runCatching {
            audit.recordFailure(actor, action, targetType, targetId, failureCode(error))
        }
        throw error
    }

    private fun failureCode(error: RuntimeException): String = when (error) {
        is com.tinder.clone.moderation.application.policy.PolicyLifecycleException -> error.code
        else -> "FAILED"
    }
}
