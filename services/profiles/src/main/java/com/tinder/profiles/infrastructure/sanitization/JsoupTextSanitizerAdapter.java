package com.tinder.profiles.infrastructure.sanitization;

import com.tinder.profiles.application.profile.port.out.TextSanitizerPort;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Jsoup-based implementation of {@link TextSanitizerPort}: strips all markup and
 * returns genuine plain text.
 *
 * <p>{@code Jsoup.clean} alone is not enough. It emits <em>HTML</em>, so it leaves
 * bare punctuation escaped — {@code Tom & Jerry} becomes {@code Tom &amp;amp; Jerry}
 * — and this value is stored in Postgres and served in JSON, where nothing decodes
 * it again. Consumers escape at render time, so the stored form must hold the real
 * characters.
 *
 * <p>Unescaping once is not enough either, and is actively unsafe: cleaning
 * {@code &amp;lt;script&amp;gt;alert(1)&amp;lt;/script&amp;gt;} yields escaped text that
 * unescapes straight back into a live {@code <script>} tag. Each unescape can
 * reveal markup the previous clean could not see, so the two are repeated until
 * the result stops changing — at that fixed point no further round can resurrect
 * a tag. Input that refuses to converge keeps the escaped form, which is inert.
 */
@Slf4j
@Component
public class JsoupTextSanitizerAdapter implements TextSanitizerPort {

    /**
     * Each round strips one layer of entity encoding. Ordinary text converges in
     * two; the cap only bounds deliberately over-encoded input.
     */
    private static final int MAX_ROUNDS = 5;

    @Override
    public String sanitizePlainText(String input) {
        if (input == null) {
            return null;
        }

        String current = input;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            String next = Parser.unescapeEntities(Jsoup.clean(current, Safelist.none()), false);
            if (next.equals(current)) {
                return current.trim();
            }
            current = next;
        }

        log.warn("Text did not converge after {} sanitization rounds; keeping the escaped form", MAX_ROUNDS);
        return Jsoup.clean(current, Safelist.none()).trim();
    }
}
