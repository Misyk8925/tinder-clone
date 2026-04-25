package com.example.swipes_demo;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class SwipeProducer {

    private static final String TOPIC = "swipe-created";

    private final KafkaSender<String, String> kafkaSender;
    private final Queue<SwipeCreatedEvent> queue;
    private final AtomicInteger queueSize = new AtomicInteger();
    private final int queueCapacity;
    private final int concurrency;
    private final int batchSize;
    private final Duration drainInterval;
    private final boolean warmupEnabled;
    private Disposable senderSubscription;

    public SwipeProducer(KafkaSender<String, String> kafkaSender,
                         @Value("${swipes.producer.queue-capacity:200000}") int queueCapacity,
                         @Value("${swipes.producer.concurrency:${swipes.producer.worker-count:4}}") int concurrency,
                         @Value("${swipes.producer.batch-size:500}") int batchSize,
                         @Value("${swipes.producer.buffer-timeout:1ms}") Duration drainInterval,
                         @Value("${swipes.producer.warmup-enabled:true}") boolean warmupEnabled) {
        this.kafkaSender = kafkaSender;
        this.queueCapacity = Math.max(1, queueCapacity);
        this.queue = new ConcurrentLinkedQueue<>();
        this.concurrency = Math.max(1, concurrency);
        this.batchSize = Math.max(1, batchSize);
        this.drainInterval = drainInterval.isNegative() || drainInterval.isZero()
                ? Duration.ofMillis(1)
                : drainInterval;
        this.warmupEnabled = warmupEnabled;
    }

    @PostConstruct
    void startSender() {
        warmProducer();
        senderSubscription = Flux.range(0, concurrency)
                .flatMap(worker -> Mono.defer(this::drainOnce).repeat(), concurrency)
                .doOnError(error -> log.error("Swipe producer pipeline stopped", error))
                .subscribe();
        log.info("Started swipe producer pipeline: concurrency={}, batchSize={}, drainInterval={}, capacity={}",
                concurrency, batchSize, drainInterval, queueCapacity);
    }

    @PreDestroy
    void stopSender() {
        if (senderSubscription != null) {
            senderSubscription.dispose();
        }
    }

    private void warmProducer() {
        if (!warmupEnabled) {
            return;
        }

        try {
            Integer partitions = kafkaSender
                    .doOnProducer(producer -> producer.partitionsFor(TOPIC).size())
                    .block(Duration.ofSeconds(15));
            log.info("Warmed Kafka producer for topic {} with {} partition(s)", TOPIC, partitions);
        } catch (Exception ex) {
            log.warn("Kafka producer warmup failed; first swipe may pay producer initialization cost", ex);
        }
    }

    public Mono<Void> send(SwipeCreatedEvent event) {
        return Mono.defer(() -> {
            if (tryEnqueue(event)) {
                return Mono.empty();
            }

            return Mono.error(new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Swipe producer queue is full"
            ));
        });
    }

    private List<SwipeCreatedEvent> drainBatch() {
        List<SwipeCreatedEvent> batch = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            SwipeCreatedEvent event = queue.poll();
            if (event == null) {
                break;
            }
            queueSize.decrementAndGet();
            batch.add(event);
        }
        return batch;
    }

    private boolean tryEnqueue(SwipeCreatedEvent event) {
        while (true) {
            int currentSize = queueSize.get();
            if (currentSize >= queueCapacity) {
                return false;
            }
            if (queueSize.compareAndSet(currentSize, currentSize + 1)) {
                queue.offer(event);
                return true;
            }
        }
    }

    private Mono<Void> drainOnce() {
        return Mono.defer(() -> {
            List<SwipeCreatedEvent> batch = drainBatch();
            if (batch.isEmpty()) {
                return Mono.delay(drainInterval).then();
            }

            return publishBatch(batch);
        }).onErrorResume(error -> {
            log.error("Failed to drain swipe producer queue", error);
            return Mono.delay(drainInterval).then();
        });
    }

    private Mono<Void> publishBatch(List<SwipeCreatedEvent> batch) {
        Flux<SenderRecord<String, String, Void>> records = Flux.fromIterable(batch)
                .map(event -> SenderRecord.create(
                        new ProducerRecord<>(TOPIC, event.getProfile1Id(), serialize(event)),
                        null
                ));

        return kafkaSender.send(records)
                .doOnError(error -> log.error("Failed to send swipe event batch", error))
                .then()
                .onErrorResume(error -> Mono.empty());
    }

    private String serialize(SwipeCreatedEvent event) {
        return "{\"eventId\":\"" + event.getEventId()
                + "\",\"profile1Id\":\"" + event.getProfile1Id()
                + "\",\"profile2Id\":\"" + event.getProfile2Id()
                + "\",\"decision\":" + event.isDecision()
                + ",\"isSuper\":" + event.isSuper()
                + ",\"timestamp\":" + event.getTimestamp()
                + "}";
    }
}
