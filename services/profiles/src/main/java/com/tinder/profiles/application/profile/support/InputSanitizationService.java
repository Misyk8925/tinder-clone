package com.tinder.profiles.application.profile.support;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
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
