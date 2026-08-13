package com.tinder.deckread.messaging;

public record SwipeSavedEvent(
        String eventId,
        String profile1Id,
        String profile2Id,
        boolean decision,
        long timestamp
) {
}
