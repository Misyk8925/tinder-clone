package com.tinder.clone.moderation.infrastructure.persistence

import com.tinder.clone.moderation.application.ports.MutationAuditPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

@Component
@ConditionalOnProperty(
    prefix = "moderation.persistence",
    name = ["mode"],
    havingValue = "jdbc",
    matchIfMissing = true
)
class JdbcMutationAudit(
    private val jdbc: JdbcTemplate,
    private val clock: Clock
) : MutationAuditPort {
    override fun recordFailure(
        actor: String,
        action: String,
        targetType: String,
        targetId: String,
        outcome: String
    ) {
        jdbc.update(
            """INSERT INTO moderation_audit_log
               (audit_id, actor, action, target_type, target_id, outcome, details_json, occurred_at)
               VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, ?)""",
            UUID.randomUUID(),
            actor.take(128),
            action.take(80),
            targetType.take(80),
            targetId.take(128),
            outcome.take(32),
            clock.instant().atOffset(ZoneOffset.UTC)
        )
    }
}
