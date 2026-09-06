package com.tinder.clone.moderation.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("moderation.security")
data class ModerationSecurityProperties(
    val users: List<InternalUser> = emptyList()
) {
    data class InternalUser(
        val username: String = "",
        val passwordHash: String = "",
        val roles: Set<String> = emptySet()
    )
}
