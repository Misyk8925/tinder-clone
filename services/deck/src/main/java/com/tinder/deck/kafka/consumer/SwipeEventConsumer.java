package com.tinder.deck.kafka.consumer;

import com.tinder.deck.kafka.dto.SwipeSavedEvent;
import com.tinder.deck.service.DeckCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer for SwipeSavedEvent messages.
 * Removes swiped profiles from the swiper's deck and marks them as swiped
 * so the profiles service can filter them out even before the next deck rebuild.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SwipeEventConsumer {

    private final DeckCache deckCache;

    @KafkaListener(
            topics = "${kafka.topics.swipe-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "swipeKafkaListenerContainerFactory"
    )
    public void consume(
            @Payload SwipeSavedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Received SwipeSavedEvent: eventId={}, profile1Id={}, profile2Id={}, decision={}, partition={}, offset={}",
                event.getEventId(), event.getProfile1Id(), event.getProfile2Id(), event.isDecision(), partition, offset);

        handleSwipeEvent(event);
        log.debug("Successfully processed SwipeSavedEvent: eventId={}", event.getEventId());
    }

    private void handleSwipeEvent(SwipeSavedEvent event) {
        log.info("Handling swipe event: swiper={}, swiped={}, decision={}",
                event.getProfile1Id(), event.getProfile2Id(), event.isDecision() ? "RIGHT" : "LEFT");

        UUID swiperId;
        UUID swipedId;
        try {
            swiperId = UUID.fromString(event.getProfile1Id());
            swipedId = UUID.fromString(event.getProfile2Id());
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format in swipe event: {}", event, e);
            throw e;
        }

        deckCache.markAsSwiped(swiperId, swipedId).block();
        log.debug("Marked profile {} as swiped in {}'s deck", swipedId, swiperId);

        Long removed = deckCache.removeFromDeck(swiperId, swipedId).block();
        if (removed != null && removed > 0) {
            log.info("Removed profile {} from {}'s deck", swipedId, swiperId);
        } else {
            log.debug("Profile {} was not in {}'s deck (already removed or deck not built yet)", swipedId, swiperId);
        }
    }
}
