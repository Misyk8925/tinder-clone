package com.tinder.clone.moderation.domain.policy

import com.tinder.clone.moderation.application.commands.output.ModerationResult
import com.tinder.clone.moderation.domain.model.Decision
import com.tinder.clone.moderation.domain.model.ModerationContent
import com.tinder.clone.moderation.domain.model.Reason
import com.tinder.clone.moderation.domain.signals.AppliedPolicy
import com.tinder.clone.moderation.domain.signals.ModerationEvidence
import com.tinder.clone.moderation.domain.signals.RuleHit
import java.time.Clock
import java.util.Date

class ModerationPolicy(
    private val catalog: ModerationPolicyCatalog,
    val version: String,
    private val applicationRules: List<Rule> = emptyList(),
    private val clock: Clock = Clock.systemUTC()
) {
    init {
        require(version.isNotBlank()) { "Policy version must not be blank" }
    }

    fun decide(content: ModerationContent, evidence: ModerationEvidence): ModerationResult.Evaluated {
        val config = catalog.resolve(version, content.type, content.locale)
            ?: return ModerationResult.Evaluated(
                Decision.Hold(Reason.POLICY_NOT_CONFIGURED),
                evidence.withAppliedPolicy(AppliedPolicy(version, null, null))
            )

        var evaluatedEvidence = evidence.withAppliedPolicy(
            AppliedPolicy(config.version, config.contentType, config.locale)
        )
        val candidates = mutableListOf<Decision>()

        applicationRules.forEach { rule ->
            rule.evaluate(evidence)?.let { decision ->
                candidates += decision
                ruleHit(rule.id, decision)?.let { evaluatedEvidence = evaluatedEvidence.withRuleHit(it) }
            }
        }

        config.thresholds.forEach { (category, threshold) ->
            val categoryScore = evidence.category(category)
            if (!categoryScore.supported) {
                candidates += Decision.Hold(Reason.CLASSIFIER_CATEGORY_UNSUPPORTED)
                evaluatedEvidence = evaluatedEvidence.withRuleHit(
                    RuleHit(
                        "unsupported:${category.name}",
                        Reason.CLASSIFIER_CATEGORY_UNSUPPORTED,
                        null,
                        category
                    )
                )
                return@forEach
            }
            val score = categoryScore.score!!.value
            val decision = when {
                score >= threshold.block -> Decision.Block(Reason.CATEGORY_BLOCK, score)
                score >= threshold.review -> Decision.Flag(Reason.CATEGORY_REVIEW, score, Date.from(clock.instant()))
                else -> null
            }
            if (decision != null) {
                candidates += decision
                evaluatedEvidence = evaluatedEvidence.withRuleHit(
                    RuleHit("threshold:${category.name}", decision.reason(), score, category)
                )
            }
        }

        return ModerationResult.Evaluated(selectDecision(candidates), evaluatedEvidence)
    }

    fun appliedPolicy(content: ModerationContent): AppliedPolicy {
        val config = catalog.resolve(version, content.type, content.locale)
        return if (config == null) {
            AppliedPolicy(version, null, null)
        } else {
            AppliedPolicy(config.version, config.contentType, config.locale)
        }
    }

    fun requiresAdjudication(content: ModerationContent, evidence: ModerationEvidence): Boolean {
        if (evidence.adjudication != null) return false
        val config = catalog.resolve(version, content.type, content.locale) ?: return false
        return config.thresholds.any { (category, threshold) ->
            val score = evidence.category(category).score?.value
            score != null && score >= threshold.review && score < threshold.block
        }
    }

    private fun selectDecision(candidates: List<Decision>): Decision =
        candidates.maxByOrNull(::severity) ?: Decision.Allow

    private fun severity(decision: Decision): Int = when (decision) {
        is Decision.Block -> 3
        is Decision.Hold -> 2
        is Decision.Flag -> 1
        Decision.Allow -> 0
    }

    private fun ruleHit(ruleId: String, decision: Decision): RuleHit? = when (decision) {
        is Decision.Block -> RuleHit(ruleId, decision.reason, decision.confidence)
        is Decision.Flag -> RuleHit(ruleId, decision.reason, decision.confidence)
        is Decision.Hold, Decision.Allow -> null
    }

    private fun Decision.reason(): Reason = when (this) {
        is Decision.Block -> reason
        is Decision.Flag -> reason
        is Decision.Hold -> reason
        Decision.Allow -> error("Allow does not produce a rule hit")
    }
}
