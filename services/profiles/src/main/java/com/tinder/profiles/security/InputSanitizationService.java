package com.tinder.profiles.security;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Service;

@Service
public class InputSanitizationService {

    public String sanitizePlainText(String input) {
        if (input == null) return null;

        // Remove all HTML tags
        String cleaned = Jsoup.clean(input, Safelist.none());
        return cleaned.trim();
    }
}
