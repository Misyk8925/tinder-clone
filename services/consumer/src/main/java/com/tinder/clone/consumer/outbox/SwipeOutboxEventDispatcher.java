package com.tinder.clone.consumer.outbox;

import com.tinder.clone.consumer.kafka.event.SwipeSavedEvent;
import com.tinder.clone.consumer.outbox.config.OutboxPublisherProperties;
import com.tinder.clone.consumer.outbox.model.SwipeEventOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class SwipeOutboxEventDispatcher {

    private final KafkaTemplate<String, SwipeSavedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxPublisherProperties properties;

    @Value("${app.kafka.topic.swipe-saved}")
    private String swipeSavedTopic;

    public void publish(SwipeEventOutbox outboxRow) {
        SwipeSavedEvent event = objectMapper.readValue(outboxRow.getPayload(), SwipeSavedEvent.class);
        String key = outboxRow.getSwiperId().toString();

        try {
            kafkaTemplate.send(swipeSavedTopic, key, event)
                    .get(properties.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
            log.debug("Published swipe outbox row={} eventId={}", outboxRow.getId(), outboxRow.getEventId());
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Throwable rootCause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("Kafka send failed for swipe event " + outboxRow.getEventId(), rootCause);
        }
    }
}
