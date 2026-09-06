package com.tinder.clone.moderation.domain.model

enum class Reason {
    SPAM,
    TOXICITY_HIGH,
    TOXICITY_MEDIUM,
    LLM_UNCERTAIN,
    POLICY_NOT_CONFIGURED,
    CLASSIFIER_CATEGORY_UNSUPPORTED,
    CATEGORY_REVIEW,
    CATEGORY_BLOCK
}
