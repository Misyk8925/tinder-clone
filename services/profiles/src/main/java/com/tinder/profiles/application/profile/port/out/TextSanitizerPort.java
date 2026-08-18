package com.tinder.profiles.application.profile.port.out;

/**
 * Outbound port for stripping markup from user-supplied text. The mechanism (an
 * HTML sanitizer library) lives in infrastructure; the application depends only
 * on this interface so it stays free of third-party text-processing libraries.
 */
public interface TextSanitizerPort {

    /** Strips HTML/markup from a plain-text value; {@code null}-safe. */
    String sanitizePlainText(String input);
}
