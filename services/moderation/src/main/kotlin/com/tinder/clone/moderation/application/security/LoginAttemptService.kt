package com.tinder.clone.moderation.application.security

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class LoginAttemptState(
    val failedAttempts: Int = 0,
    val lockedUntil: Instant? = null
)

interface LoginAttemptStore {
    fun load(username: String): LoginAttemptState
    fun recordFailure(
        username: String,
        now: Instant,
        threshold: Int,
        lockDuration: Duration
    ): LoginAttemptState
    fun clear(username: String)
}

class InMemoryLoginAttemptStore : LoginAttemptStore {
    private val states = ConcurrentHashMap<String, LoginAttemptState>()
    override fun load(username: String) = states[username] ?: LoginAttemptState()
    @Synchronized
    override fun recordFailure(
        username: String,
        now: Instant,
        threshold: Int,
        lockDuration: Duration
    ): LoginAttemptState {
        val current = states[username] ?: LoginAttemptState()
        if (current.lockedUntil?.isAfter(now) == true) return current
        val previousFailures = if (current.lockedUntil != null) 0 else current.failedAttempts
        val failed = previousFailures + 1
        val updated = LoginAttemptState(
            failed,
            if (failed >= threshold) now.plus(lockDuration) else null
        )
        states[username] = updated
        return updated
    }
    override fun clear(username: String) {
        states.remove(username)
    }
}

class LoginAttemptService(
    private val store: LoginAttemptStore,
    private val clock: Clock,
    private val threshold: Int = 5,
    private val lockDuration: Duration = Duration.ofMinutes(15)
) {
    fun isLocked(username: String): Boolean {
        val until = store.load(username).lockedUntil ?: return false
        return until.isAfter(clock.instant())
    }

    fun recordFailure(username: String) {
        if (username.isBlank()) return
        store.recordFailure(username, clock.instant(), threshold, lockDuration)
    }

    fun recordSuccess(username: String) {
        store.clear(username)
    }
}
