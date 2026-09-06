package com.tinder.clone.moderation.domain

import com.tinder.clone.moderation.domain.model.ModerationContent
import com.tinder.clone.moderation.application.commands.output.ModerationResult
import com.tinder.clone.moderation.domain.policy.ModerationPolicy
import com.tinder.clone.moderation.domain.signals.ModerationEvidence
import com.tinder.clone.moderation.domain.signals.AppliedPolicy

fun interface ModerationPolicyProvider {
    fun policyFor(content: ModerationContent): ModerationPolicy
}

class ModerationDomainService(private val policyProvider: ModerationPolicyProvider) {
    constructor(policy: ModerationPolicy) : this(ModerationPolicyProvider { policy })

    fun requiresAdjudication(content: ModerationContent, evidence: ModerationEvidence): Boolean =
        policyProvider.policyFor(content).requiresAdjudication(content, evidence)

    fun appliedPolicy(content: ModerationContent): AppliedPolicy = policyProvider.policyFor(content).appliedPolicy(content)

    fun moderate(
        content: ModerationContent,
        evidence: ModerationEvidence
    ): ModerationResult.Evaluated {

        // content можно использовать позже (например, длина, язык и т.д.)

        return policyProvider.policyFor(content).decide(content, evidence)
    }
}
