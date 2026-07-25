package com.tinder.profiles.infrastructure.sanitization;

import com.tinder.profiles.application.profile.port.out.TextSanitizerPort;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Jsoup-based implementation of {@link TextSanitizerPort}: removes all HTML tags
 * and trims the result.
 */
@Component
public class JsoupTextSanitizer implements TextSanitizerPort {

    @Override
    public String sanitizePlainText(String input) {
        if (input == null) {
            return null;
        }
        return Jsoup.clean(input, Safelist.none()).trim();
    }
}
