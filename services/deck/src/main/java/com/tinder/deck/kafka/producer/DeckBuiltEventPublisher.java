package com.tinder.deck.kafka.producer;

import com.tinder.contracts.event.v1.DeckBuiltEventV1;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import reactor.util.retry.Retry;

/** Repairable notification published only after the source Redis snapshot is stable. */
@Component
@Slf4j
public class DeckBuiltEventPublisher {

    private final KafkaTemplate<String, DeckBuiltEventV1> kafka;
    private final String topic;
    private final Counter published;
    private final Counter failed;

    public DeckBuiltEventPublisher(
            @Qualifier("deckBuiltKafkaTemplate") KafkaTemplate<String, DeckBuiltEventV1> kafka,
            @Value("${kafka.topics.deck-built:deck.built.v1}") String topic,
            MeterRegistry meters
    ) {
        this.kafka = kafka;
        this.topic = topic;
        this.published = meters.counter("deck.build.events", "outcome", "published");
        this.failed = meters.counter("deck.build.events", "outcome", "failed");
    }

    public Mono<Void> publish(UUID viewerProfileId, String buildTimestamp, int candidateCount) {
        DeckBuiltEventV1 event = new DeckBuiltEventV1(
                UUID.randomUUID(), viewerProfileId, buildTimestamp, candidateCount, Instant.now());
        return Mono.fromFuture(kafka.send(topic, viewerProfileId.toString(), event))
                .doOnSuccess(ignored -> published.increment())
                .retryWhen(Retry.backoff(2, Duration.ofMillis(50)).maxBackoff(Duration.ofMillis(250)))
                .doOnError(error -> {
                    failed.increment();
                    log.warn("Deck snapshot {} was stored but its build event was not published; repair paths will retry",
                            viewerProfileId, error);
                })
                .then();
    }
}
