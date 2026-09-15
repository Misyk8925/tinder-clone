package com.tinder.clone.moderation.infrastructure.http

import org.springframework.http.MediaType
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RestController

@RestController
class LoginController {
    @GetMapping("/login", produces = [MediaType.TEXT_HTML_VALUE])
    fun login(@RequestAttribute("_csrf") csrf: CsrfToken): String = """
        <!doctype html>
        <html lang="en">
        <head><meta charset="utf-8"><title>Moderation login</title></head>
        <body>
          <main>
            <h1>Moderation admin</h1>
            <form method="post" action="/login">
              <input type="hidden" name="${escape(csrf.parameterName)}" value="${escape(csrf.token)}">
              <label>Username <input name="username" autocomplete="username"></label>
              <label>Password <input name="password" type="password" autocomplete="current-password"></label>
              <button type="submit">Sign in</button>
            </form>
          </main>
        </body>
        </html>
    """.trimIndent()

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
