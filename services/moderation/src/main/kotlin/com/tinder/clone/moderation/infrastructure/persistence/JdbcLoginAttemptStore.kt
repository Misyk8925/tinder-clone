package com.tinder.clone.moderation.infrastructure.persistence

import com.tinder.clone.moderation.application.security.LoginAttemptState
import com.tinder.clone.moderation.application.security.LoginAttemptStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Component
@ConditionalOnProperty(prefix = "moderation.persistence", name = ["mode"], havingValue = "jdbc", matchIfMissing = true)
class JdbcLoginAttemptStore(
    private val jdbc: JdbcTemplate,
    private val clock: Clock
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

    override fun save(username: String, state: LoginAttemptState) {
        jdbc.update(
            """INSERT INTO moderation_login_attempt (username, failed_attempts, locked_until, updated_at)
               VALUES (?, ?, ?, ?)
               ON CONFLICT (username) DO UPDATE SET failed_attempts = EXCLUDED.failed_attempts,
                    locked_until = EXCLUDED.locked_until, updated_at = EXCLUDED.updated_at""",
            username,
            state.failedAttempts,
            state.lockedUntil?.atOffset(ZoneOffset.UTC),
            clock.instant().atOffset(ZoneOffset.UTC)
        )
    }

    override fun clear(username: String) {
        jdbc.update("DELETE FROM moderation_login_attempt WHERE username = ?", username)
    }
}
