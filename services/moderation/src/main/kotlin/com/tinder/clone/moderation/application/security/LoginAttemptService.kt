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
    fun save(username: String, state: LoginAttemptState)
    fun clear(username: String)
}

class InMemoryLoginAttemptStore : LoginAttemptStore {
    private val states = ConcurrentHashMap<String, LoginAttemptState>()
    override fun load(username: String) = states[username] ?: LoginAttemptState()
    override fun save(username: String, state: LoginAttemptState) {
        states[username] = state
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
        val current = store.load(username)
        if (current.lockedUntil?.isAfter(clock.instant()) == true) return
        val failed = current.failedAttempts + 1
        val lockedUntil = if (failed >= threshold) clock.instant().plus(lockDuration) else null
        store.save(username, LoginAttemptState(failed, lockedUntil))
    }

    fun recordSuccess(username: String) {
        store.clear(username)
    }
}
