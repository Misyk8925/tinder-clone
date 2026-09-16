package com.tinder.clone.moderation.infrastructure.persistence

import com.tinder.clone.moderation.application.security.LoginAttemptState
import com.tinder.clone.moderation.application.security.LoginAttemptStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Component
@ConditionalOnProperty(prefix = "moderation.persistence", name = ["mode"], havingValue = "jdbc", matchIfMissing = true)
class JdbcLoginAttemptStore(
    private val jdbc: JdbcTemplate
) : LoginAttemptStore {
    override fun load(username: String): LoginAttemptState {
        val rows = jdbc.query(
            "SELECT failed_attempts, locked_until FROM moderation_login_attempt WHERE username = ?",
            RowMapper { rs, _ ->
                LoginAttemptState(
                    rs.getInt("failed_attempts"),
                    rs.getObject("locked_until", OffsetDateTime::class.java)?.toInstant()
                )
            },
            username
        )
        return rows.singleOrNull() ?: LoginAttemptState()
    }

    override fun recordFailure(
        username: String,
        now: Instant,
        threshold: Int,
        lockDuration: Duration
    ): LoginAttemptState = requireNotNull(
        jdbc.queryForObject(
            """INSERT INTO moderation_login_attempt (username, failed_attempts, locked_until, updated_at)
               VALUES (?, 1, CASE WHEN ? <= 1 THEN ? ELSE NULL END, ?)
               ON CONFLICT (username) DO UPDATE SET
                 failed_attempts = CASE
                   WHEN moderation_login_attempt.locked_until > EXCLUDED.updated_at
                     THEN moderation_login_attempt.failed_attempts
                   WHEN moderation_login_attempt.locked_until IS NOT NULL
                     THEN 1
                   ELSE moderation_login_attempt.failed_attempts + 1
                 END,
                 locked_until = CASE
                   WHEN moderation_login_attempt.locked_until > EXCLUDED.updated_at
                     THEN moderation_login_attempt.locked_until
                   WHEN (CASE
                     WHEN moderation_login_attempt.locked_until IS NOT NULL THEN 1
                     ELSE moderation_login_attempt.failed_attempts + 1
                   END) >= ?
                     THEN ?
                   ELSE NULL
                 END,
                 updated_at = EXCLUDED.updated_at
               RETURNING failed_attempts, locked_until""",
            RowMapper { rs, _ ->
                LoginAttemptState(
                    rs.getInt("failed_attempts"),
                    rs.getObject("locked_until", OffsetDateTime::class.java)?.toInstant()
                )
            },
            username,
            threshold,
            now.plus(lockDuration).atOffset(ZoneOffset.UTC),
            now.atOffset(ZoneOffset.UTC),
            threshold,
            now.plus(lockDuration).atOffset(ZoneOffset.UTC)
        )
    )

    override fun clear(username: String) {
        jdbc.update("DELETE FROM moderation_login_attempt WHERE username = ?", username)
    }
}
