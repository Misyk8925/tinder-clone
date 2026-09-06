package com.tinder.clone.moderation.domain.policy

import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.ModerationCategory
import java.util.Collections

data class CategoryThreshold(val review: Double, val block: Double) {
    init {
        require(review in 0.0..1.0) { "Review threshold must be between 0 and 1" }
        require(block in 0.0..1.0) { "Block threshold must be between 0 and 1" }
        require(review <= block) { "Review threshold must not exceed block threshold" }
    }
}

data class ModerationPolicyConfig(
    val version: String,
    val contentType: ContentType?,
    val locale: String?,
    val thresholds: Map<ModerationCategory, CategoryThreshold>
) {
    init {
        require(version.isNotBlank()) { "Policy version must not be blank" }
        require(locale == null || locale.isNotBlank()) { "Policy locale must not be blank" }
        require(locale == null || contentType != null) { "Locale policy requires a content type" }
    }

    internal fun published(): ModerationPolicyConfig = copy(
        thresholds = Collections.unmodifiableMap(LinkedHashMap(thresholds))
    )
}

class ModerationPolicyCatalog(configurations: Collection<ModerationPolicyConfig>) {
    private val configurations = configurations.map(ModerationPolicyConfig::published)

    init {
        val scopes = this.configurations.map { Triple(it.version, it.contentType, it.locale?.lowercase()) }
        require(scopes.size == scopes.distinct().size) { "Policy version contains duplicate scopes" }
    }

    fun resolve(version: String, contentType: ContentType, locale: String?): ModerationPolicyConfig? {
        val candidates = configurations.filter { it.version == version }
        return candidates.firstOrNull {
            it.contentType == contentType && it.locale != null && it.locale.equals(locale, ignoreCase = true)
        } ?: candidates.firstOrNull {
            it.contentType == contentType && it.locale == null
        } ?: candidates.firstOrNull {
            it.contentType == null && it.locale == null
        }
    }
}

@DslMarker
annotation class ModerationPolicyDslMarker

fun moderationPolicies(block: ModerationPoliciesDsl.() -> Unit): ModerationPolicyCatalog =
    ModerationPoliciesDsl().apply(block).build()

@ModerationPolicyDslMarker
class ModerationPoliciesDsl {
    private val configurations = mutableListOf<ModerationPolicyConfig>()

    fun version(value: String, block: PolicyVersionDsl.() -> Unit) {
        configurations += PolicyVersionDsl(value).apply(block).build()
    }

    internal fun build() = ModerationPolicyCatalog(configurations)
}

@ModerationPolicyDslMarker
class PolicyVersionDsl(private val version: String) {
    private val configurations = mutableListOf<ModerationPolicyConfig>()

    fun global(block: ThresholdsDsl.() -> Unit) = add(null, null, block)

    fun contentType(type: ContentType, block: ThresholdsDsl.() -> Unit) = add(type, null, block)

    fun locale(type: ContentType, locale: String, block: ThresholdsDsl.() -> Unit) = add(type, locale, block)

    private fun add(type: ContentType?, locale: String?, block: ThresholdsDsl.() -> Unit) {
        configurations += ModerationPolicyConfig(
            version = version,
            contentType = type,
            locale = locale,
            thresholds = ThresholdsDsl().apply(block).build()
        )
    }

    internal fun build(): List<ModerationPolicyConfig> = configurations.toList()
}

@ModerationPolicyDslMarker
class ThresholdsDsl {
    private val thresholds = linkedMapOf<ModerationCategory, CategoryThreshold>()

    fun category(category: ModerationCategory, review: Double, block: Double) {
        require(category !in thresholds) { "Threshold for $category is already configured" }
        thresholds[category] = CategoryThreshold(review, block)
    }

    internal fun build(): Map<ModerationCategory, CategoryThreshold> = thresholds.toMap()
}
