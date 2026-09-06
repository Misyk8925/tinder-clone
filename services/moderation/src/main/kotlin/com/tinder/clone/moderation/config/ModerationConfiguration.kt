package com.tinder.clone.moderation.config

import com.tinder.clone.moderation.application.ports.LlmPort
import com.tinder.clone.moderation.application.ports.ModerationClassifierPort
import com.tinder.clone.moderation.application.ports.input.ModerateContentInputPort
import com.tinder.clone.moderation.application.service.EvidenceBuilder
import com.tinder.clone.moderation.application.service.PreModerationProcessor
import com.tinder.clone.moderation.application.service.SlidingWindowTrafficLimiter
import com.tinder.clone.moderation.application.service.ModerationExecutionService
import com.tinder.clone.moderation.application.service.ModerationDecisionStore
import com.tinder.clone.moderation.application.service.InMemoryModerationDecisionStore
import com.tinder.clone.moderation.application.usecase.ModerateContentUsecase
import com.tinder.clone.moderation.domain.ModerationDomainService
import com.tinder.clone.moderation.application.policy.PolicyStateStore
import com.tinder.clone.moderation.application.policy.InMemoryPolicyStateStore
import com.tinder.clone.moderation.application.policy.RuntimePolicyRegistry
import com.tinder.clone.moderation.infrastructure.persistence.JdbcModerationDecisionStore
import com.tinder.clone.moderation.infrastructure.persistence.JdbcPolicyStateStore
import com.tinder.clone.moderation.infrastructure.provider.GeminiLlmAdapter
import com.tinder.clone.moderation.infrastructure.provider.GeminiProperties
import com.tinder.clone.moderation.infrastructure.provider.OpenAiModerationAdapter
import com.tinder.clone.moderation.infrastructure.provider.OpenAiModerationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry

@Configuration
@EnableConfigurationProperties(
    OpenAiModerationProperties::class,
    GeminiProperties::class,
    ModerationRuntimeProperties::class,
    ModerationSecurityProperties::class,
    ModerationTrafficProperties::class
)
class ModerationConfiguration {
    @Bean
    fun classifierPort(properties: OpenAiModerationProperties, objectMapper: ObjectMapper): ModerationClassifierPort =
        OpenAiModerationAdapter(properties, objectMapper)

    @Bean
    fun llmPort(properties: GeminiProperties, objectMapper: ObjectMapper): LlmPort =
        GeminiLlmAdapter(properties, objectMapper)

    @Bean
    @ConditionalOnProperty(prefix = "moderation.persistence", name = ["mode"], havingValue = "memory")
    fun inMemoryPolicyStateStore(): PolicyStateStore = InMemoryPolicyStateStore()

    @Bean
    @ConditionalOnProperty(prefix = "moderation.persistence", name = ["mode"], havingValue = "jdbc", matchIfMissing = true)
    fun jdbcPolicyStateStore(jdbc: JdbcTemplate, objectMapper: ObjectMapper): PolicyStateStore =
        JdbcPolicyStateStore(jdbc, objectMapper)

    @Bean
    @ConditionalOnProperty(prefix = "moderation.persistence", name = ["mode"], havingValue = "memory")
    fun inMemoryModerationDecisionStore(): ModerationDecisionStore = InMemoryModerationDecisionStore()

    @Bean
    @ConditionalOnProperty(prefix = "moderation.persistence", name = ["mode"], havingValue = "jdbc", matchIfMissing = true)
    fun jdbcModerationDecisionStore(jdbc: JdbcTemplate, objectMapper: ObjectMapper): ModerationDecisionStore =
        JdbcModerationDecisionStore(jdbc, objectMapper)

    @Bean
    fun runtimePolicyRegistry(store: PolicyStateStore): RuntimePolicyRegistry = RuntimePolicyRegistry(store = store)

    @Bean
    fun preModerationProcessor(traffic: ModerationTrafficProperties): PreModerationProcessor =
        PreModerationProcessor(
            maxTextChars = 65_536,
            trafficLimiter = SlidingWindowTrafficLimiter(traffic.requestsPerMinute)
        )

    @Bean
    fun moderateContentInputPort(
        classifierPort: ModerationClassifierPort,
        llmPort: LlmPort,
        policies: RuntimePolicyRegistry,
        preModerationProcessor: PreModerationProcessor
    ): ModerateContentInputPort = ModerateContentUsecase(
        classifierPort,
        llmPort,
        ModerationDomainService(policies),
        EvidenceBuilder(),
        preModerationProcessor
    )

    @Bean
    fun moderationExecutionService(
        input: ModerateContentInputPort,
        objectMapper: ObjectMapper,
        store: ModerationDecisionStore,
        meterRegistry: MeterRegistry
    ): ModerationExecutionService = ModerationExecutionService(input, objectMapper, store, meterRegistry = meterRegistry)
}
