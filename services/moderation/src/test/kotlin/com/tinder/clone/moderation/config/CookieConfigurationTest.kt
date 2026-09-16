package com.tinder.clone.moderation.config

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class CookieConfigurationTest {
    @Test
    fun `session cookie is secure by default and only local profile disables secure`() {
        val defaults = Files.readString(Path.of("src/main/resources/application.yaml"))
        val local = Files.readString(Path.of("src/main/resources/application-local.yaml"))

        assertTrue(defaults.contains("secure: \${MODERATION_COOKIE_SECURE:true}"))
        assertTrue(local.contains("secure: false"))
    }
}
