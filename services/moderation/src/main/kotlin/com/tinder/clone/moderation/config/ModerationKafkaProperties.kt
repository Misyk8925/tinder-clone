package com.tinder.clone.moderation.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "moderation.kafka")
data class ModerationKafkaProperties(
    val enabled: Boolean = false,
    val commandsTopic: String = "moderation.commands.v1",
    val resultsTopic: String = "moderation.results.v1",
    val reviewsTopic: String = "moderation.reviews.v1",
    val policiesTopic: String = "moderation.policies.v1",
    val commandsDlqTopic: String = "moderation.commands.dlq.v1",
    val groupId: String = "moderation-service"
)
