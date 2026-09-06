package com.tinder.clone.moderation.infrastructure.http

import com.tinder.clone.moderation.application.policy.RuntimePolicyRegistry
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
class PolicyController(private val policies: RuntimePolicyRegistry) {
    @PostMapping("/policies")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: PolicyDraftRequestDto, principal: Principal): PolicyVersionDto =
        policies.create(request.version, request.description, request.toDefinitions(), principal.name).toDto()

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
        require(version == request.version) { "Path and body policy versions must match" }
        return policies.replaceDraft(
            version,
            parseVersion(ifMatch),
            request.description,
            request.toDefinitions(),
            principal.name
        ).toDto()
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
    ): PolicyVersionDto = policies.publish(version, parseVersion(ifMatch), principal.name).toDto()

    @PutMapping("/policies/{version}/activation")
    fun activate(
        @PathVariable version: String,
        @RequestBody scope: PolicyScopeDto,
        principal: Principal
    ): PolicyActivationDto = policies.activate(version, scope.contentType, scope.locale, principal.name).toDto()

    @PostMapping("/policy-activations/{activationId}/rollback")
    fun rollback(
        @PathVariable activationId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        principal: Principal
    ): PolicyActivationDto = policies.rollback(activationId, parseVersion(ifMatch), principal.name).toDto()

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
}
