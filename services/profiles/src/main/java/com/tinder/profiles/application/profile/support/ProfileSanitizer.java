package com.tinder.profiles.application.profile.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sanitizes free-text profile fields at the application boundary (when the API
 * mapper builds a command). Wraps {@link InputSanitizationService}, consolidating
 * the sanitization that previously lived in {@code sanitizeProfileData} (full
 * update) and the field-by-field calls in the legacy patch path.
 */
@Component
@RequiredArgsConstructor
public class ProfileSanitizer {

    private final InputSanitizationService sanitizationService;

    /** Sanitizes a plain-text value, preserving {@code null}. */
    public String sanitize(String value) {
        return value == null ? null : sanitizationService.sanitizePlainText(value);
    }
}
