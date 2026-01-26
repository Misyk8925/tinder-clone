package com.tinder.deck.kafka.consumer;

import com.tinder.deck.kafka.dto.ProfileEvent;
import com.tinder.deck.service.DeckCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileEventConsumer {

    private final DeckCache deckCache;

    @KafkaListener(
            topics = "${kafka.topics.profile-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload ProfileEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("Received ProfileEvent: eventId={}, profileId={}, changeType={}, partition={}, offset={}",
                event.getEventId(), event.getProfileId(), event.getChangeType(), partition, offset);

        try {
            handleProfileEvent(event);

            acknowledgment.acknowledge();

            log.debug("Successfully processed ProfileEvent: eventId={}", event.getEventId());

        } catch (Exception e) {
            log.error("Error processing ProfileEvent: eventId={}, profileId={}",
                    event.getEventId(), event.getProfileId(), e);

            // TODO: Implement retry logic or DLQ (Dead Letter Queue)
            // For now, acknowledge to prevent infinite retry
            acknowledgment.acknowledge();
        }
    }

    private void handleProfileEvent(ProfileEvent event) {
        log.info("Handling profile event: profileId={}, changeType={}, fields={}",
                event.getProfileId(), event.getChangeType(), event.getChangedFields());

        switch (event.getChangeType()) {
            case PREFERENCES -> handlePreferencesChange(event);
            case CRITICAL_FIELDS -> handleCriticalFieldsChange(event);
            case NON_CRITICAL -> handleNonCriticalChange(event);
            default -> log.warn("Unknown change type: {}", event.getChangeType());
        }
    }

    private void handlePreferencesChange(ProfileEvent event) {
        log.info("PREFERENCES changed for profile: {}. Invalidating personal deck only", event.getProfileId());

        try {
            Long count = deckCache.invalidate(event.getProfileId()).block();
            if (count != null && count > 0) {
                log.info("Invalidated personal deck for profile: {}", event.getProfileId());
            } else {
                log.debug("No personal deck found for profile: {}", event.getProfileId());
            }
        } catch (Exception error) {
            log.error("Failed to invalidate personal deck", error);
            throw error;
        }
    }

    private void handleCriticalFieldsChange(ProfileEvent event) {
        log.info("CRITICAL_FIELDS changed for profile: {}. Invalidating personal deck",
                event.getProfileId());

        try {
            Long count = deckCache.invalidate(event.getProfileId()).block();
            if (count != null && count > 0) {
                log.info("Invalidated personal deck after critical field change: {}",
                        event.getProfileId());
            }
        } catch (Exception error) {
            log.error("Failed to invalidate personal deck", error);
            throw error;
        }

        try {
            deckCache.markAsStaleForAllDecks(event.getProfileId()).block();
        } catch (Exception error) {
            log.error("Failed to mark profile as stale across decks", error);
            throw error;
        }

        log.debug("Preferences caches will expire naturally via TTL. " +
                 "Stale data acceptable for up to 5 minutes.");
    }

    private void handleNonCriticalChange(ProfileEvent event) {
        log.debug("NON_CRITICAL changes for profile: {}. No cache invalidation required",
                event.getProfileId());
        // No action needed - changes don't affect deck eligibility
    }

}
