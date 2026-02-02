package com.tinder.deck.kafka.consumer;

import com.tinder.deck.kafka.dto.ProfileDeleteEvent;
import com.tinder.deck.kafka.dto.ProfileUpdateEvent;
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
    public void consumeProfileUpdate(
            @Payload ProfileUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("Received ProfileEvent: eventId={}, profileId={}, changeType={}, partition={}, offset={}",
                event.getEventId(), event.getProfileId(), event.getChangeType(), partition, offset);

        try {
            handleProfileUpdateEvent(event);

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

    @KafkaListener(
            topics = "${kafka.topics.delete-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "deleteKafkaListenerContainerFactory"
    )
    public void consumeProfileDeletion(
            @Payload ProfileDeleteEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("RECEIVED PROFILE DELETION Event: eventId={}, profileId={}, partition={}, offset={}",
                event.getEventId(), event.getProfileId(), partition, offset);

        try {
            log.info("Invalidating all decks for deleted profile: {}", event.getProfileId());

            deckCache.markAsStaleForAllDecks(event.getProfileId())
                    .doOnError(error -> log.error("Failed to mark deleted profile as stale across decks", error))
                    .subscribe();
            deckCache.invalidate(event.getProfileId())
                    .doOnSuccess(count -> {
                        if (count > 0) {
                            log.info("Invalidated decks for deleted profile: {}", event.getProfileId());
                        } else {
                            log.debug("No decks found for deleted profile: {}", event.getProfileId());
                        }
                    })
                    .doOnError(error -> log.error("Failed to invalidate decks for deleted profile", error))
                    .subscribe();

            acknowledgment.acknowledge();

            log.debug("Successfully processed Profile Deletion Event: eventId={}", event.getEventId());

        } catch (Exception e) {
            log.error("Error processing Profile Deletion Event: eventId={}, profileId={}",
                    event.getEventId(), event.getProfileId(), e);
            // Acknowledge to prevent infinite retry
            acknowledgment.acknowledge();
        }
    }


    private void handleProfileUpdateEvent(ProfileUpdateEvent event) {
        log.info("Handling profile event: profileId={}, changeType={}, fields={}",
                event.getProfileId(), event.getChangeType(), event.getChangedFields());

        switch (event.getChangeType()) {
            case PREFERENCES -> handlePreferencesChange(event);
            case CRITICAL_FIELDS -> handleCriticalFieldsChange(event);
            case LOCATION_CHANGE -> handleLocationChange(event);
            case NON_CRITICAL -> handleNonCriticalChange(event);
            default -> log.warn("Unknown change type: {}", event.getChangeType());
        }
    }

    private void handlePreferencesChange(ProfileUpdateEvent event) {
        log.info("PREFERENCES changed for profile: {}. Invalidating personal deck only", event.getProfileId());

        deckCache.invalidate(event.getProfileId())
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Invalidated personal deck for profile: {}", event.getProfileId());
                    } else {
                        log.debug("No personal deck found for profile: {}", event.getProfileId());
                    }
                })
                .doOnError(error -> log.error("Failed to invalidate personal deck", error))
                .subscribe();
    }

    private void handleCriticalFieldsChange(ProfileUpdateEvent event) {
        log.info("CRITICAL_FIELDS changed for profile: {}. Invalidating personal deck",
                event.getProfileId());


        deckCache.markAsStaleForAllDecks(event.getProfileId())
                .doOnError(error -> log.error("Failed to mark profile as stale across decks", error))
                .subscribe();

        log.debug("Preferences caches will expire naturally via TTL. " +
                 "Stale data acceptable for up to 5 minutes.");
    }

    private void handleLocationChange(ProfileUpdateEvent event) {
        log.info("LOCATION_CHANGE for profile: {}. Invalidating personal deck and marking stale for viewers",
                event.getProfileId());

        deckCache.invalidate(event.getProfileId())
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Invalidated personal deck for profile: {}", event.getProfileId());
                    } else {
                        log.debug("No personal deck found for profile: {}", event.getProfileId());
                    }
                })
                .doOnError(error -> log.error("Failed to invalidate personal deck after location change", error))
                .subscribe();

        deckCache.markAsStaleForAllDecks(event.getProfileId())
                .doOnError(error -> log.error("Failed to mark profile as stale across decks after location change", error))
                .subscribe();
    }

    private void handleNonCriticalChange(ProfileUpdateEvent event) {
        log.debug("NON_CRITICAL changes for profile: {}. No cache invalidation required",
                event.getProfileId());
        // No action needed - changes don't affect deck eligibility
    }

}
