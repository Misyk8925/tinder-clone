package com.tinder.clone.moderation.application.usecase

import com.tinder.clone.moderation.application.commands.input.ContentCmd
import com.tinder.clone.moderation.application.commands.output.CategoryScore
import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.application.commands.output.LlmResult
import com.tinder.clone.moderation.application.commands.output.ModerationResult
import com.tinder.clone.moderation.application.ports.LlmPort
import com.tinder.clone.moderation.application.commands.input.LlmAnalysisRequest
import com.tinder.clone.moderation.application.ports.ModerationClassifierPort
import com.tinder.clone.moderation.application.service.EvidenceBuilder
import com.tinder.clone.moderation.application.service.PreModerationProcessor
import com.tinder.clone.moderation.application.service.ApplicationSignalProvider
import com.tinder.clone.moderation.application.service.ValidationReason
import com.tinder.clone.moderation.application.service.TrafficLimiter
import com.tinder.clone.moderation.common.enums.LlmLabel
import com.tinder.clone.moderation.domain.ModerationDomainService
import com.tinder.clone.moderation.domain.model.Decision
import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.ContextMessage
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.ModerationContent
import com.tinder.clone.moderation.domain.policy.ModerationPolicy
import com.tinder.clone.moderation.domain.policy.VerifiedScamIndicatorRule
import com.tinder.clone.moderation.domain.policy.Rule
import com.tinder.clone.moderation.domain.policy.moderationPolicies
import com.tinder.clone.moderation.domain.signals.ApplicationSignalType
import com.tinder.clone.moderation.domain.signals.ApplicationSignal
import com.tinder.clone.moderation.infrastructure.provider.FallbackClassifier
import com.tinder.clone.moderation.infrastructure.provider.FallbackLlmAdapter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import java.time.Duration

class ModerateContentUsecaseTest {

    @Test
    fun `oversized conversation context stops before classifier`() {
        val classifier = CountingClassifierPort()
        val usecase = ModerateContentUsecase(
            classifier,
            CountingLlmPort(),
            ModerationDomainService(testPolicy()),
            EvidenceBuilder(),
            PreModerationProcessor(maxContextBytes = 8)
        )

        val result = usecase.handle(
            ContentCmd(
                "current",
                ContentType.MESSAGE,
                "hello",
                conversationContext = listOf(ContextMessage("previous", "other", "too much context"))
            )
        )

        assertEquals(ValidationReason.PAYLOAD_TOO_LARGE, assertIs<ModerationResult.Invalid>(result).reason)
        assertEquals(0, classifier.calls)
    }

    @Test
    fun `llm adjudication receives normalized content context classifier signals and policy`() {
        val classifierPort = CountingClassifierPort()
        val llmPort = CountingLlmPort()
        val policy = ModerationPolicy(
            catalog = moderationPolicies {
                version("context-v1") {
                    global { category(ModerationCategory.HARASSMENT, review = 0.05, block = 0.9) }
                }
            },
            version = "context-v1"
        )
        val usecase = ModerateContentUsecase(
            classifierPort,
            llmPort,
            ModerationDomainService(policy),
            EvidenceBuilder(),
            PreModerationProcessor()
        )
        val context = listOf(ContextMessage("previous", "other-user", "It is a book quote"))

        usecase.handle(
            ContentCmd(
                "current",
                ContentType.MESSAGE,
                "ｈｅｌｌｏ",
                locale = "en",
                authorId = "subject-user",
                conversationContext = context
            )
        )

        assertEquals(1, llmPort.calls)
        val request = requireNotNull(llmPort.lastRequest)
        assertEquals("hello", request.content.text)
        assertEquals(context, request.content.conversationContext)
        assertEquals("test", request.classifierResult.provider)
        assertEquals("context-v1", request.appliedPolicy.version)
        assertEquals(emptyList(), request.applicationSignals)
    }

    @Test
    fun `urls do not bypass classifier or become a block`() {
        val classifierPort = CountingClassifierPort()
        val llmPort = CountingLlmPort()
        val policy = testPolicy()
        val domainService = ModerationDomainService(policy)

        val usecase = ModerateContentUsecase(
            classifierPort = classifierPort,
            llmPort = llmPort,
            domainService = domainService,
            signalBuilder = EvidenceBuilder(),
            preModerationProcessor = PreModerationProcessor()
        )

        val decision = usecase.handle(
            ContentCmd(
                contentId = "content-1",
                contentType = ContentType.MESSAGE,
                text = "Visit http://a.example and http://b.example now"
            )
        )

        val evaluated = assertIs<ModerationResult.Evaluated>(decision)
        assertIs<Decision.Allow>(evaluated.decision)
        assertEquals(ApplicationSignalType.URL_COUNT, evaluated.evidence.applicationSignals.single().type)
        assertEquals(1, classifierPort.calls)
        assertEquals(0, llmPort.calls)
    }

    @Test
    fun `contextual risk words should be analyzed by classifier with full context`() {
        val classifierPort = CountingClassifierPort()
        val llmPort = CountingLlmPort()
        val policy = testPolicy()
        val usecase = ModerateContentUsecase(
            classifierPort = classifierPort,
            llmPort = llmPort,
            domainService = ModerationDomainService(policy),
            signalBuilder = EvidenceBuilder(),
            preModerationProcessor = PreModerationProcessor()
        )

        val decision = usecase.handle(
            ContentCmd(
                contentId = "content-2",
                contentType = ContentType.MESSAGE,
                text = "The news quoted a film character saying I will kill you",
                imageUrls = listOf("https://cdn.example/context.jpg"),
                locale = "en-AT",
                country = "AT",
                authorId = "user-1",
                conversationContext = listOf(
                    ContextMessage("context-1", "user-2", "This is a film quote")
                )
            )
        )

        assertIs<Decision.Allow>(assertIs<ModerationResult.Evaluated>(decision).decision)
        assertEquals(1, classifierPort.calls)
        assertEquals("content-2", classifierPort.lastContent?.id)
        assertEquals(ContentType.MESSAGE, classifierPort.lastContent?.type)
        assertEquals(
            "The news quoted a film character saying I will kill you",
            classifierPort.lastContent?.text
        )
        assertEquals("en-AT", classifierPort.lastContent?.locale)
        assertEquals("AT", classifierPort.lastContent?.country)
        assertEquals("user-1", classifierPort.lastContent?.authorId)
        assertEquals(listOf("https://cdn.example/context.jpg"), classifierPort.lastContent?.imageUrls)
        assertEquals(1, classifierPort.lastContent?.conversationContext?.size)
        assertEquals(0, llmPort.calls)
    }

    @Test
    fun `image only content should reach multimodal classifier without calling text llm`() {
        val classifierPort = CountingClassifierPort()
        val llmPort = CountingLlmPort()
        val usecase = ModerateContentUsecase(
            classifierPort = classifierPort,
            llmPort = llmPort,
            domainService = ModerationDomainService(testPolicy()),
            signalBuilder = EvidenceBuilder(),
            preModerationProcessor = PreModerationProcessor()
        )

        val decision = usecase.handle(
            ContentCmd(
                contentId = "photo-1",
                contentType = ContentType.PHOTO,
                text = null,
                imageUrls = listOf("https://cdn.example/photo.jpg"),
                authorId = "user-1"
            )
        )

        assertIs<Decision.Allow>(assertIs<ModerationResult.Evaluated>(decision).decision)
        assertEquals(1, classifierPort.calls)
        assertEquals(null, classifierPort.lastContent?.text)
        assertEquals(listOf("https://cdn.example/photo.jpg"), classifierPort.lastContent?.imageUrls)
        assertEquals(0, llmPort.calls)
    }

    @Test
    fun `application signal reaches policy and produces an auditable rule hit`() {
        val classifierPort = CountingClassifierPort()
        val processor = PreModerationProcessor(signalProviders = listOf(
            ApplicationSignalProvider {
                listOf(ApplicationSignal(ApplicationSignalType.VERIFIED_SCAM_INDICATOR, mapOf("indicatorId" to "scam-7")))
            }
        ))
        val usecase = ModerateContentUsecase(
            classifierPort = classifierPort,
            llmPort = CountingLlmPort(),
            domainService = ModerationDomainService(testPolicy(listOf(VerifiedScamIndicatorRule()))),
            signalBuilder = EvidenceBuilder(),
            preModerationProcessor = processor
        )

        val result = assertIs<ModerationResult.Evaluated>(usecase.handle(
            ContentCmd("content-3", ContentType.MESSAGE, "contact me")
        ))

        assertIs<Decision.Block>(result.decision)
        assertEquals(ApplicationSignalType.VERIFIED_SCAM_INDICATOR, result.evidence.applicationSignals.single().type)
        assertEquals("verified-scam-indicator", result.evidence.ruleHits.single().ruleId)
        assertEquals(1, classifierPort.calls)
    }

    @Test
    fun `invalid payload is not a content block and does not call classifier`() {
        val classifierPort = CountingClassifierPort()
        val usecase = ModerateContentUsecase(
            classifierPort = classifierPort,
            llmPort = CountingLlmPort(),
            domainService = ModerationDomainService(testPolicy()),
            signalBuilder = EvidenceBuilder(),
            preModerationProcessor = PreModerationProcessor(maxTextChars = 4)
        )

        val oversized = assertIs<ModerationResult.Invalid>(
            usecase.handle(ContentCmd("content-4", ContentType.MESSAGE, "12345"))
        )
        val malformed = assertIs<ModerationResult.Invalid>(
            usecase.handle(ContentCmd(" ", ContentType.MESSAGE, "hello"))
        )

        assertEquals(ValidationReason.PAYLOAD_TOO_LARGE, oversized.reason)
        assertEquals(ValidationReason.MALFORMED_PAYLOAD, malformed.reason)
        assertEquals(0, classifierPort.calls)
    }

    @Test
    fun `fallback classifier plus default tinder thresholds blocks hate bios and harassment messages`() {
        val usecase = ModerateContentUsecase(
            FallbackClassifier(),
            FallbackLlmAdapter(),
            ModerationDomainService(tinderDefaultPolicy()),
            EvidenceBuilder(),
            PreModerationProcessor()
        )

        val hateBio = assertIs<ModerationResult.Evaluated>(
            usecase.handle(
                ContentCmd(
                    "profile:hate",
                    ContentType.PROFILE_DESCRIPTION,
                    "I hate all outsiders and they should die"
                )
            )
        )
        val hateMessage = assertIs<ModerationResult.Evaluated>(
            usecase.handle(ContentCmd("message:hate", ContentType.MESSAGE, "kill yourself"))
        )
        val cleanBio = assertIs<ModerationResult.Evaluated>(
            usecase.handle(
                ContentCmd("profile:clean", ContentType.PROFILE_DESCRIPTION, "Coffee and a long walk")
            )
        )

        assertIs<Decision.Block>(hateBio.decision)
        assertIs<Decision.Block>(hateMessage.decision)
        assertEquals(Decision.Allow, cleanBio.decision)
    }

    @Test
    fun `rate limited payload stops before classifier`() {
        val classifierPort = CountingClassifierPort()
        val retryAfter = Duration.ofSeconds(30)
        val usecase = ModerateContentUsecase(
            classifierPort = classifierPort,
            llmPort = CountingLlmPort(),
            domainService = ModerationDomainService(testPolicy()),
            signalBuilder = EvidenceBuilder(),
            preModerationProcessor = PreModerationProcessor(trafficLimiter = TrafficLimiter { retryAfter })
        )

        val result = assertIs<ModerationResult.Throttled>(
            usecase.handle(ContentCmd("content-5", ContentType.MESSAGE, "hello"))
        )

        assertEquals(retryAfter, result.retryAfter)
        assertEquals(0, classifierPort.calls)
    }

    private class CountingClassifierPort : ModerationClassifierPort {
        var calls = 0
        var lastContent: ModerationContent? = null

        override fun classify(content: ModerationContent): ClassificationResult {
            calls += 1
            lastContent = content
            return ClassificationResult(
                provider = "test",
                model = "safe-classifier",
                modelSnapshot = "v1",
                categories = ModerationCategory.entries.associateWith { CategoryScore.unsupported() } + mapOf(
                    ModerationCategory.HARASSMENT to CategoryScore.supported(0.1, flagged = false),
                    ModerationCategory.SPAM to CategoryScore.supported(0.1, flagged = false)
                ),
                flagged = false,
                latencyMs = 1
            )
        }
    }

    private fun testPolicy(applicationRules: List<Rule> = emptyList()) = ModerationPolicy(
        catalog = moderationPolicies { version("test") { global { } } },
        version = "test",
        applicationRules = applicationRules
    )

    private fun tinderDefaultPolicy() = ModerationPolicy(
        catalog = moderationPolicies {
            version("tinder-default-v1") {
                global {
                    category(ModerationCategory.HARASSMENT, review = 0.55, block = 0.85)
                    category(ModerationCategory.HARASSMENT_THREATENING, review = 0.35, block = 0.70)
                    category(ModerationCategory.HATE, review = 0.50, block = 0.80)
                    category(ModerationCategory.HATE_THREATENING, review = 0.30, block = 0.65)
                    category(ModerationCategory.SEXUAL_CONTENT, review = 0.60, block = 0.90)
                    category(ModerationCategory.SEXUAL_MINORS, review = 0.10, block = 0.25)
                    category(ModerationCategory.SELF_HARM, review = 0.35, block = 0.70)
                    category(ModerationCategory.VIOLENCE, review = 0.45, block = 0.80)
                }
            }
        },
        version = "tinder-default-v1"
    )

    private class CountingLlmPort : LlmPort {
        var calls = 0
        var lastRequest: LlmAnalysisRequest? = null

        override fun analyzeContent(request: LlmAnalysisRequest): LlmResult {
            calls += 1
            lastRequest = request
            return LlmResult(label = LlmLabel.SAFE, confidence = 0.6)
        }
    }
}
