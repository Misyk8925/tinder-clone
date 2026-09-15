package com.tinder.clone.moderation.application.ports

interface MutationAuditPort {
    fun recordFailure(
        actor: String,
        action: String,
        targetType: String,
        targetId: String,
        outcome: String
    )
}

class NoOpMutationAudit : MutationAuditPort {
    override fun recordFailure(
        actor: String,
        action: String,
        targetType: String,
        targetId: String,
        outcome: String
    ) = Unit
}
