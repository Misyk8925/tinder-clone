package com.example.swipes_demo;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class SwipeProducer {

    private static final String TOPIC = "swipe-created";

    private final KafkaSender<String, String> kafkaSender;
    private final BlockingQueue<SwipeCreatedEvent> queue;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final int queueCapacity;
    private final int workerCount;
    private final int batchSize;
    private final boolean warmupEnabled;
    private ExecutorService workers;

    public SwipeProducer(KafkaSender<String, String> kafkaSender,
                         @Value("${swipes.producer.queue-capacity:200000}") int queueCapacity,
                         @Value("${swipes.producer.worker-count:4}") int workerCount,
                         @Value("${swipes.producer.batch-size:500}") int batchSize,
                         @Value("${swipes.producer.warmup-enabled:true}") boolean warmupEnabled) {
        this.kafkaSender = kafkaSender;
        this.queueCapacity = Math.max(1, queueCapacity);
        this.queue = new ArrayBlockingQueue<>(this.queueCapacity);
        this.workerCount = Math.max(1, workerCount);
        this.batchSize = Math.max(1, batchSize);
        this.warmupEnabled = warmupEnabled;
    }

    @PostConstruct
    void startWorkers() {
        warmProducer();
        running.set(true);
        workers = Executors.newFixedThreadPool(workerCount, new SwipeProducerThreadFactory());
        for (int i = 0; i < workerCount; i++) {
            workers.submit(this::drainLoop);
        }
        log.info("Started swipe producer queue workers: workers={}, batchSize={}, capacity={}",
                workerCount, batchSize, queueCapacity);
    }

    @PreDestroy
    void stopWorkers() {
        running.set(false);
        if (workers != null) {
            workers.shutdownNow();
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
            if (queue.offer(event)) {
                return Mono.empty();
            }

            return Mono.error(new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Swipe producer queue is full"
            ));
        });
    }

    private void drainLoop() {
        List<SwipeCreatedEvent> batch = new ArrayList<>(batchSize);

        while (running.get() || !queue.isEmpty()) {
            try {
                SwipeCreatedEvent first = queue.poll(250, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }

                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
                publishBatch(batch);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                if (!running.get()) {
                    return;
                }
            } catch (Exception ex) {
                log.error("Failed to publish swipe batch", ex);
            } finally {
                batch.clear();
            }
        }
    }

    private void publishBatch(List<SwipeCreatedEvent> batch) {
        Flux<SenderRecord<String, String, Void>> records = Flux.fromIterable(batch)
                .map(event -> SenderRecord.create(
                        new ProducerRecord<>(TOPIC, event.getProfile1Id(), serialize(event)),
                        null
                ));

        kafkaSender.send(records)
                .doOnError(error -> log.error("Failed to send swipe event batch", error))
                .then()
                .block(Duration.ofSeconds(30));
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

    private static final class SwipeProducerThreadFactory implements ThreadFactory {
        private int index = 1;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "swipe-producer-" + index++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
