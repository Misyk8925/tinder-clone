# Versioned moderation policy DSL

Published moderation thresholds are configured through the Kotlin DSL:

```kotlin
val catalog = moderationPolicies {
    version("2026-09-03") {
        global {
            category(HARASSMENT, review = 0.50, block = 0.90)
        }
        contentType(PROFILE_DESCRIPTION) {
            category(HARASSMENT, review = 0.70, block = 0.95)
        }
        locale(MESSAGE, "de-AT") {
            category(HARASSMENT, review = 0.60, block = 0.92)
        }
    }
}

val policy = ModerationPolicy(catalog, version = "2026-09-03")
```

Resolution order is `content type + locale`, then `content type`, then the global scope.
The catalog defensively copies every threshold map at publication and exposes no mutation API.
Every evaluated result records the selected version and scope in `ModerationEvidence.appliedPolicy`.

Missing version/scope produces `Decision.Hold(POLICY_NOT_CONFIGURED)`. A category required by the
selected policy but unsupported by the classifier produces
`Decision.Hold(CLASSIFIER_CATEGORY_UNSUPPORTED)`. Neither case silently allows content.
