package com.tinder.clone.moderation.infrastructure.provider

class ProviderException(
    val provider: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
