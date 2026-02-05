package com.tinder.deck.kafka.consumer;

import com.tinder.deck.kafka.dto.ProfileDeleteEvent;
import com.tinder.deck.kafka.dto.ProfileUpdateEvent;
import com.tinder.deck.service.DeckCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Received ProfileEvent: eventId={}, profileId={}, changeType={}, partition={}, offset={}",
                event.getEventId(), event.getProfileId(), event.getChangeType(), partition, offset);

        handleProfileUpdateEvent(event).block();
        log.debug("Successfully processed ProfileEvent: eventId={}", event.getEventId());
    }

    @KafkaListener(
            topics = "${kafka.topics.delete-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "deleteKafkaListenerContainerFactory"
    )
    public void consumeProfileDeletion(
            @Payload ProfileDeleteEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("RECEIVED PROFILE DELETION Event: eventId={}, profileId={}, partition={}, offset={}",
                event.getEventId(), event.getProfileId(), partition, offset);

        log.info("Invalidating decks for deleted profile: {}", event.getProfileId());

        deckCache.markAsStaleForAllDecks(event.getProfileId())
                .doOnNext(count -> log.info("Marked deleted profile {} as stale in {} decks", event.getProfileId(), count))
                .then(deckCache.invalidate(event.getProfileId())
                        .doOnNext(count -> {
                            if (count > 0) {
                                log.info("Invalidated personal deck for deleted profile: {}", event.getProfileId());
                            } else {
                                log.debug("No personal deck found for deleted profile: {}", event.getProfileId());
                            }
                        }))
                .then()
                .block();

        log.debug("Successfully processed Profile Deletion Event: eventId={}", event.getEventId());
    }


    private Mono<Void> handleProfileUpdateEvent(ProfileUpdateEvent event) {
        log.info("Handling profile event: profileId={}, changeType={}, fields={}",
                event.getProfileId(), event.getChangeType(), event.getChangedFields());

        return switch (event.getChangeType()) {
            case PREFERENCES -> handlePreferencesChange(event);
            case CRITICAL_FIELDS -> handleCriticalFieldsChange(event);
            case LOCATION_CHANGE -> handleLocationChange(event);
            case NON_CRITICAL -> handleNonCriticalChange(event);
            default -> Mono.fromRunnable(() -> log.warn("Unknown change type: {}", event.getChangeType()));
        };
    }

    private Mono<Void> handlePreferencesChange(ProfileUpdateEvent event) {
        log.info("PREFERENCES changed for profile: {}. Invalidating personal deck only", event.getProfileId());

        return deckCache.invalidate(event.getProfileId())
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Invalidated personal deck for profile: {}", event.getProfileId());
                    } else {
                        log.debug("No personal deck found for profile: {}", event.getProfileId());
                    }
                })
                .then();
    }

    private Mono<Void> handleCriticalFieldsChange(ProfileUpdateEvent event) {
        log.info("CRITICAL_FIELDS changed for profile: {}. Invalidating personal deck",
                event.getProfileId());

        return deckCache.markAsStaleForAllDecks(event.getProfileId())
                .doOnNext(count -> log.info("Marked profile {} as stale in {} decks", event.getProfileId(), count))
                .then(Mono.fromRunnable(() -> log.debug("Preferences caches will expire naturally via TTL. " +
                        "Stale data acceptable for up to 5 minutes.")));
    }

    private Mono<Void> handleLocationChange(ProfileUpdateEvent event) {
        log.info("LOCATION_CHANGE for profile: {}. Invalidating personal deck and marking stale for viewers",
                event.getProfileId());

        Mono<Long> invalidate = deckCache.invalidate(event.getProfileId())
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Invalidated personal deck for profile: {}", event.getProfileId());
                    } else {
                        log.debug("No personal deck found for profile: {}", event.getProfileId());
                    }
                })
                .doOnError(error -> log.error("Failed to invalidate personal deck after location change", error));

        Mono<Long> markStale = deckCache.markAsStaleForAllDecks(event.getProfileId())
                .doOnNext(count -> log.info("Marked profile {} as stale in {} decks", event.getProfileId(), count))
                .doOnError(error -> log.error("Failed to mark profile as stale across decks after location change", error));

        return invalidate.then(markStale).then();
    }

    private Mono<Void> handleNonCriticalChange(ProfileUpdateEvent event) {
        return Mono.fromRunnable(() ->
                log.debug("NON_CRITICAL changes for profile: {}. No cache invalidation required",
                        event.getProfileId()));
    }

}
