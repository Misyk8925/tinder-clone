package com.tinder.clone.moderation.infrastructure.messaging

import com.tinder.clone.moderation.application.commands.input.ContentCmd
import com.tinder.clone.moderation.application.commands.output.CategoryScore
import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.application.commands.output.ModerationResult
import com.tinder.clone.moderation.application.ports.input.ModerateContentInputPort
import com.tinder.clone.moderation.application.service.InMemoryModerationDecisionStore
import com.tinder.clone.moderation.application.service.ModerationExecutionService
import com.tinder.clone.moderation.config.ModerationKafkaProperties
import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.Decision
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.signals.AppliedPolicy
import com.tinder.clone.moderation.domain.signals.ModerationEvidence
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModerationMessagingTest {
    private val mapper = jacksonObjectMapper()
    private val clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC)
    private val kafka = ModerationKafkaProperties(enabled = true)

    @Test
    fun `command consumer evaluates a versioned event using the message id as the idempotency key`() {
        val executions = ModerationExecutionService(
            input = AllowingUseCase(),
            objectMapper = mapper,
            store = InMemoryModerationDecisionStore()
        )
        val consumer = ModerationCommandConsumer(executions, mapper)
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val payload = commandJson(messageId, "This quoted sentence needs context")
        consumer.process(payload)
        consumer.process(payload)
        assertEquals(1, executions.list().size)
        val second = executions.execute(messageId.toString(), com.tinder.clone.moderation.infrastructure.http.ModerationRequestDto(
            "message-42", ContentType.MESSAGE, "This quoted sentence needs context",
            conversationContext = listOf(
                com.tinder.clone.moderation.infrastructure.http.ContextMessageDto("message-41", "other-1", "This quoted sentence needs context")
            )
        ))
        assertTrue((second as com.tinder.clone.moderation.application.service.ModerationExecutionOutcome.Evaluated).response.replayed)
    }

    @Test
    fun `poison command without an evaluated decision is retried by throwing`() {
        val executions = ModerationExecutionService(
            input = InvalidUseCase(),
            objectMapper = mapper,
            store = InMemoryModerationDecisionStore()
        )
        val consumer = ModerationCommandConsumer(executions, mapper)
        assertFailsWith<CommandNotEvaluatedException> {
            consumer.process(commandJson(UUID.randomUUID(), "x"))
        }
    }

    @Test
    fun `DLQ payload hashes the original command and omits raw conversation text`() {
        val published = mutableListOf<Triple<String, String, String>>()
        val dlq = ModerationCommandDlq(
            publisher = EventPublisher { topic, key, payload -> published += Triple(topic, key, payload) },
            objectMapper = mapper,
            kafka = kafka,
            clock = clock
        )
        val secret = "SECRET_CONVERSATION_MARKER_XYZ"
        val messageId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        dlq.publish(commandJson(messageId, secret), RuntimeException("classifier timeout"), 5)

        val payload = published.single().third
        assertEquals(kafka.commandsDlqTopic, published.single().first)
        assertEquals(messageId.toString(), published.single().second)
        assertTrue(payload.contains("\"attempts\":5"))
        assertTrue(payload.contains("\"originalMessageId\":\"$messageId\""))
        assertTrue(payload.contains("payloadSha256"))
        assertFalse(payload.contains(secret))
        assertFalse(payload.contains("conversationContext"))
        assertTrue(payload.contains("\"diagnostic\":\"Processing failed: RuntimeException\""))
    }

    @Test
    fun `DLQ diagnostic never includes malformed payload tokens`() {
        val published = mutableListOf<String>()
        val dlq = ModerationCommandDlq(
            EventPublisher { _, _, payload -> published += payload },
            mapper,
            kafka,
            clock
        )
        val secret = "SECRET_MALFORMED_PAYLOAD_XYZ"
        dlq.publish("{\"messageId\":\"broken\",\"text\":\"$secret", RuntimeException("Unexpected token $secret"))
        assertFalse(published.single().contains(secret))
    }

    @Test
    fun `v1 consumer rejects commands with another schema version`() {
        val executions = ModerationExecutionService(
            input = AllowingUseCase(),
            objectMapper = mapper,
            store = InMemoryModerationDecisionStore()
        )
        val consumer = ModerationCommandConsumer(executions, mapper)
        val payload = commandJson(UUID.randomUUID(), "hello").replace("\"schemaVersion\":1", "\"schemaVersion\":2")
        assertFailsWith<IllegalArgumentException> { consumer.process(payload) }
        assertTrue(executions.list().isEmpty())
    }

    @Test
    fun `outbox publisher retries a failed send and publishes after restart`() {
        val records = InMemoryOutboxRecordRepository()
        val failing = FailingThenSucceedingPublisher()
        val publisher = ModerationOutboxPublisher(records, failing, clock)
        val row = OutboxRecord(
            id = UUID.randomUUID(),
            aggregateType = "ModerationDecision",
            aggregateId = "dec-1",
            topic = kafka.resultsTopic,
            messageKey = "content-1",
            payload = """{"decision":"ALLOW"}""",
            createdAt = clock.instant(),
            nextAttemptAt = clock.instant()
        )
        records.insert(row)

        publisher.publishPending()
        assertEquals(1, records.unpublishedCount())
        assertEquals(1, records.due(clock.instant().plusSeconds(120)).single().attemptCount)

        failing.fail = false
        val later = OutboxRecord(
            id = row.id,
            aggregateType = row.aggregateType,
            aggregateId = row.aggregateId,
            topic = row.topic,
            messageKey = row.messageKey,
            payload = row.payload,
            createdAt = row.createdAt,
            attemptCount = 1,
            nextAttemptAt = clock.instant()
        )
        records.markFailed(row.id, 1, clock.instant(), "broker down")
        publisher.publishOne(later)
        assertEquals(0, records.unpublishedCount())
        assertEquals(1, failing.sent.size)
    }

    @Test
    fun `completed decisions enqueue a result event without raw context`() {
        val records = InMemoryOutboxRecordRepository()
        val outbox = JdbcModerationOutbox(records, mapper, kafka, clock)
        val request = com.tinder.clone.moderation.infrastructure.http.ModerationRequestDto(
            "message-42",
            ContentType.MESSAGE,
            "SECRET_RAW_TEXT",
            conversationContext = listOf(
                com.tinder.clone.moderation.infrastructure.http.ContextMessageDto("prev", "other", "SECRET_CONTEXT")
            )
        )
        val evidence = com.tinder.clone.moderation.infrastructure.http.EvidenceDto(
            "fallback", "keyword", null, emptyList(), emptyList(), null, emptyList(),
            "tinder-default-v1", mapOf("contentType" to "MESSAGE", "locale" to null)
        )
        val response = com.tinder.clone.moderation.infrastructure.http.ModerationResponseDto(
            UUID.randomUUID(), "message-42", "ALLOW", null, null, evidence, createdAt = clock.instant()
        )
        outbox.enqueueCompleted(request, response, UUID.randomUUID())
        val payload = records.all().single { it.topic == kafka.resultsTopic }.payload
        assertTrue(payload.contains("\"decision\":\"ALLOW\""))
        assertFalse(payload.contains("SECRET_RAW_TEXT"))
        assertFalse(payload.contains("SECRET_CONTEXT"))
    }

    private fun commandJson(messageId: UUID, text: String) = """
        {
          "schemaVersion":1,
          "messageId":"$messageId",
          "correlationId":"message-42",
          "occurredAt":"2026-09-15T12:00:00Z",
          "contentId":"message-42",
          "contentType":"MESSAGE",
          "text":"$text",
          "conversationContext":[{"contentId":"message-41","authorId":"other-1","text":"$text"}]
        }
    """.trimIndent()

    private class AllowingUseCase : ModerateContentInputPort {
        override fun handle(contentCmd: ContentCmd): ModerationResult {
            val categories = ModerationCategory.entries.associateWith { CategoryScore.unsupported() }
            val evidence = ModerationEvidence(
                ClassificationResult("test", "none", null, categories, false, 0)
            ).withAppliedPolicy(AppliedPolicy("tinder-default-v1", contentCmd.contentType, null))
            return ModerationResult.Evaluated(Decision.Allow, evidence)
        }
    }

    private class InvalidUseCase : ModerateContentInputPort {
        override fun handle(contentCmd: ContentCmd) =
            ModerationResult.Invalid(com.tinder.clone.moderation.application.service.ValidationReason.EMPTY_CONTENT)
    }

    private class FailingThenSucceedingPublisher : EventPublisher {
        var fail = true
        val sent = mutableListOf<String>()
        override fun send(topic: String, key: String, payload: String) {
            if (fail) throw IllegalStateException("broker down")
            sent += payload
        }
    }
}
