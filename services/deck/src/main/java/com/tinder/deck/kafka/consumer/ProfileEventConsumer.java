package com.tinder.deck.kafka.consumer;

import com.tinder.contracts.event.v1.ProfileDeletedEvent;
import com.tinder.contracts.event.v1.ProfileUpdatedEvent;
import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.service.DeckCache;
import com.tinder.deck.service.DeckService;
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
    private final ProfilesHttp profilesHttp;
    private final DeckService deckService;

    @KafkaListener(
            topics = "${kafka.topics.profile-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeProfileUpdate(
            @Payload ProfileUpdatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Received ProfileEvent: eventId={}, profileId={}, changeType={}, partition={}, offset={}",
                event.eventId(), event.profileId(), event.changeType(), partition, offset);

        handleProfileUpdatedEvent(event).block();
        log.debug("Successfully processed ProfileEvent: eventId={}", event.eventId());
    }

    @KafkaListener(
            topics = "${kafka.topics.delete-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "deleteKafkaListenerContainerFactory"
    )
    public void consumeProfileDeletion(
            @Payload ProfileDeletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("RECEIVED PROFILE DELETION Event: eventId={}, profileId={}, partition={}, offset={}",
                event.eventId(), event.profileId(), partition, offset);

        log.info("Invalidating decks for deleted profile: {}", event.profileId());

        deckCache.markProfileDeleted(event.profileId())
                .doOnNext(marked -> log.info("Marked deleted profile {} in global deleted set={}",
                        event.profileId(), marked))
                .then(deckCache.markProfileInvalidated(event.profileId())
                .doOnNext(marked -> log.info("Marked deleted profile {} as globally invalidated={}",
                        event.profileId(), marked))
                .then(deckCache.invalidate(event.profileId())
                        .doOnNext(count -> {
                            if (count > 0) {
                                log.info("Invalidated personal deck for deleted profile: {}", event.profileId());
                            } else {
                                log.debug("No personal deck found for deleted profile: {}", event.profileId());
                            }
                        }))
                // Eagerly purge the deleted profile from every other viewer's deck via the
                // reverse index, so the read side never serves a deleted profile.
                .then(deckCache.removeFromAllDecks(event.profileId())
                        .doOnNext(count -> log.info("Purged deleted profile {} from {} decks",
                                event.profileId(), count))))
                .then()
                .block();

        log.debug("Successfully processed Profile Deletion Event: eventId={}", event.eventId());
    }


    private Mono<Void> handleProfileUpdatedEvent(ProfileUpdatedEvent event) {
        log.info("Handling profile event: profileId={}, changeType={}, fields={}",
                event.profileId(), event.changeType(), event.changedFields());

        return switch (event.changeType()) {
            case PREFERENCES -> handlePreferencesChange(event);
            case CRITICAL_FIELDS -> handleCriticalFieldsChange(event);
            case LOCATION_CHANGE -> handleLocationChange(event);
            case NON_CRITICAL -> handleNonCriticalChange(event);
            default -> Mono.fromRunnable(() -> log.warn("Unknown change type: {}", event.changeType()));
        };
    }

    private Mono<Void> handlePreferencesChange(ProfileUpdatedEvent event) {
        log.info("PREFERENCES changed for profile: {}. Invalidating personal deck only", event.profileId());

        return deckCache.invalidate(event.profileId())
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Invalidated personal deck for profile: {}", event.profileId());
                    } else {
                        log.debug("No personal deck found for profile: {}", event.profileId());
                    }
                })
                .then();
    }

    private Mono<Void> handleCriticalFieldsChange(ProfileUpdatedEvent event) {
        log.info("CRITICAL_FIELDS changed for profile: {}. Globally invalidating cached entries",
                event.profileId());

        return deckCache.markProfileInvalidated(event.profileId())
                .doOnNext(marked -> log.info("Marked profile {} as globally invalidated={}",
                        event.profileId(), marked))
                // Eagerly remove the now-stale profile from every deck that contains it; the
                // next rebuild re-adds it (re-scored) if it still matches. Keeps the read side
                // a pure pass-through.
                .then(deckCache.removeFromAllDecks(event.profileId())
                        .doOnNext(count -> log.info("Purged critically-changed profile {} from {} decks",
                                event.profileId(), count)))
                .then();
    }

    private Mono<Void> handleLocationChange(ProfileUpdatedEvent event) {
        log.info("LOCATION_CHANGE for profile: {}. Invalidating personal deck, globally invalidating, and triggering rebuild",
                event.profileId());

        Mono<Long> invalidate = deckCache.invalidate(event.profileId())
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Invalidated personal deck for profile: {}", event.profileId());
                    } else {
                        log.debug("No personal deck found for profile: {}", event.profileId());
                    }
                })
                .doOnError(error -> log.error("Failed to invalidate personal deck after location change", error));

        Mono<Boolean> markInvalidated = deckCache.markProfileInvalidated(event.profileId())
                .doOnNext(marked -> log.info("Marked profile {} as globally invalidated={}",
                        event.profileId(), marked))
                .doOnError(error -> log.error("Failed to globally invalidate profile after location change", error));

        Mono<Long> purgeFromAllDecks = deckCache.removeFromAllDecks(event.profileId())
                .doOnNext(count -> log.info("Purged location-changed profile {} from {} decks",
                        event.profileId(), count))
                .doOnError(error -> log.error("Failed to purge profile from decks after location change", error));

        // Proactively rebuild the viewer's deck with candidates near the new location.
        // Pipeline: search candidates (new coords) → drop already-swiped → score by proximity → cache ordered.
        // Non-fatal: if the profiles service is slow or this errors, the deck rebuilds lazily on next request.
        Mono<Void> triggerRebuild = profilesHttp.getProfile(event.profileId())
                .flatMap(viewer -> deckService.rebuildOneDeck(viewer))
                .doOnSuccess(v -> log.info("Proactive deck rebuild completed for profile {} after location change",
                        event.profileId()))
                .onErrorResume(e -> {
                    log.warn("Proactive deck rebuild after location change failed for profile {}: {}",
                            event.profileId(), e.getMessage());
                    return Mono.empty();
                });

        return invalidate.then(markInvalidated).then(purgeFromAllDecks).then(triggerRebuild);
    }

    private Mono<Void> handleNonCriticalChange(ProfileUpdatedEvent event) {
        return Mono.fromRunnable(() ->
                log.debug("NON_CRITICAL changes for profile: {}. No cache invalidation required",
                        event.profileId()));
    }

}
