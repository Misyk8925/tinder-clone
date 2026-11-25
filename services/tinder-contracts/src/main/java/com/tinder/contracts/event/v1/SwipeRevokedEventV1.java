package com.tinder.contracts.event.v1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Append-only compensating event published to {@value #TOPIC} when a viewer rewinds
 * their latest swipe within the 5-minute window and before a match.
 * Records are keyed by {@code profile1Id} (swiper profile UUID).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwipeRevokedEventV1(
        @NotBlank String eventId,
        @NotBlank String revokedSwipeEventId,
        @NotBlank String profile1Id,
        @NotBlank String profile2Id,
        boolean originalDecision,
        @Positive long swipedAt,
        @Positive long revokedAt
) {
    public static final String TOPIC = "swipe-revoked";
}
