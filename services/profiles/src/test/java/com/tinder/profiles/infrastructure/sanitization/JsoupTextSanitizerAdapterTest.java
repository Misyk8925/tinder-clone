package com.tinder.profiles.infrastructure.sanitization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * The sanitizer is the service's only defence against stored markup in
 * user-supplied profile text, so its behaviour is pinned here rather than left to
 * the Jsoup defaults.
 */
@DisplayName("JsoupTextSanitizerAdapter")
class JsoupTextSanitizerAdapterTest {

    private final JsoupTextSanitizerAdapter sanitizer = new JsoupTextSanitizerAdapter();

    @Test
    @DisplayName("is null-safe")
    void isNullSafe() {
        then(sanitizer.sanitizePlainText(null)).isNull();
    }

    @Test
    @DisplayName("leaves ordinary prose untouched")
    void leavesProseUntouched() {
        then(sanitizer.sanitizePlainText("Hiker, cook, dog person."))
                .isEqualTo("Hiker, cook, dog person.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert('xss')</script>",
            "<img src=x onerror=alert(1)>",
            "<iframe src=\"https://evil.example\"></iframe>",
            "<svg/onload=alert(1)>",
            "<a href=\"javascript:alert(1)\"></a>",
            "<style>body{display:none}</style>"
    })
    @DisplayName("strips markup that carries script payloads")
    void stripsScriptPayloads(String malicious) {
        String cleaned = sanitizer.sanitizePlainText(malicious);

        then(cleaned).doesNotContain("<", ">");
        then(cleaned.toLowerCase()).doesNotContain("script", "onerror", "onload", "javascript:");
    }

    @Test
    @DisplayName("keeps the text content of a formatting tag while dropping the tag")
    void keepsTextDropsTag() {
        then(sanitizer.sanitizePlainText("I love <b>hiking</b>")).isEqualTo("I love hiking");
    }

    @Test
    @DisplayName("trims surrounding whitespace")
    void trimsWhitespace() {
        then(sanitizer.sanitizePlainText("   Vienna   ")).isEqualTo("Vienna");
    }

    @Test
    @DisplayName("preserves non-ASCII text")
    void preservesNonAsciiText() {
        then(sanitizer.sanitizePlainText("café <b>Wien</b>")).isEqualTo("café Wien");
    }

    /**
     * The value is stored in Postgres and served in JSON; consumers escape at
     * render time. So punctuation must survive as the real character — an escaped
     * entity here is corrupted data that renders literally downstream.
     */
    @Test
    @DisplayName("leaves bare punctuation as real characters, not HTML entities")
    void doesNotHtmlEscapeBarePunctuation() {
        then(sanitizer.sanitizePlainText("Tom & Jerry")).isEqualTo("Tom & Jerry");
        then(sanitizer.sanitizePlainText("5 < 10")).isEqualTo("5 < 10");
        then(sanitizer.sanitizePlainText("a > b")).isEqualTo("a > b");
        then(sanitizer.sanitizePlainText("AT&T")).isEqualTo("AT&T");
        then(sanitizer.sanitizePlainText("100% & <3")).isEqualTo("100% & <3");
    }

    /**
     * The reason unescaping cannot be done in a single pass. Cleaning an
     * entity-encoded tag leaves escaped text that unescapes back into live markup,
     * so a naive {@code unescape(clean(x))} — and {@code Jsoup.parse(x).text()} —
     * hand back a working {@code <script>}. Only iterating to a fixed point is safe.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "&lt;script&gt;alert(1)&lt;/script&gt;",
            "&amp;lt;script&amp;gt;alert(1)&amp;lt;/script&amp;gt;",
            "&amp;amp;lt;script&amp;amp;gt;",
            "&lt;img src=x onerror=alert(1)&gt;",
            "&lt;svg/onload=alert(1)&gt;"
    })
    @DisplayName("does not resurrect markup hidden behind entity encoding")
    void doesNotResurrectEntityEncodedMarkup(String smuggled) {
        String cleaned = sanitizer.sanitizePlainText(smuggled);

        then(cleaned).doesNotContain("<", ">");
        then(cleaned.toLowerCase()).doesNotContain("script", "onerror", "onload");
    }

    @Test
    @DisplayName("is idempotent — sanitizing already-clean text changes nothing")
    void isIdempotent() {
        for (String input : new String[]{"Tom & Jerry", "I love hiking", "café", "5 < 10"}) {
            String once = sanitizer.sanitizePlainText(input);
            then(sanitizer.sanitizePlainText(once)).isEqualTo(once);
        }
    }
}
