package com.tinder.clone.moderation.application.service

import com.tinder.clone.moderation.application.commands.input.ContentCmd
import com.tinder.clone.moderation.application.commands.output.CategoryScore
import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.application.commands.output.ModerationResult
import com.tinder.clone.moderation.application.ports.ModerationOutboxPort
import com.tinder.clone.moderation.application.ports.input.ModerateContentInputPort
import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.Decision
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.signals.AppliedPolicy
import com.tinder.clone.moderation.domain.signals.ModerationEvidence
import com.tinder.clone.moderation.infrastructure.http.ModerationRequestDto
import com.tinder.clone.moderation.infrastructure.http.ModerationResponseDto
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModerationExecutionOutboxTest {
    @Test
    fun `a newly created decision is written to the outbox once`() {
        val outbox = RecordingOutbox()
        val service = ModerationExecutionService(
            input = AllowingUseCase(),
            objectMapper = jacksonObjectMapper(),
            store = InMemoryModerationDecisionStore(),
            outbox = outbox
        )

        val request = ModerationRequestDto("bio-1", ContentType.PROFILE_DESCRIPTION, "hello")
        val first = service.execute("idem-key-1", request)
        val replay = service.execute("idem-key-1", request)

        assertTrue(first is ModerationExecutionOutcome.Evaluated)
        assertTrue(replay is ModerationExecutionOutcome.Evaluated)
        assertEquals(1, outbox.calls)
    }

    private class AllowingUseCase : ModerateContentInputPort {
        override fun handle(contentCmd: ContentCmd): ModerationResult {
            val categories = ModerationCategory.entries.associateWith { CategoryScore.unsupported() }
            val evidence = ModerationEvidence(
                ClassificationResult("test", "none", null, categories, false, 0)
            ).withAppliedPolicy(AppliedPolicy("tinder-default-v1", contentCmd.contentType, null))
            return ModerationResult.Evaluated(Decision.Allow, evidence)
        }
    }

    private class RecordingOutbox : ModerationOutboxPort {
        var calls = 0
        override fun enqueueCompleted(
            request: ModerationRequestDto,
            response: ModerationResponseDto,
            requestMessageId: UUID?
        ) {
            calls += 1
        }
    }
}
