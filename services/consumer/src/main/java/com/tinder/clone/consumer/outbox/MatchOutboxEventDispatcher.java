package com.tinder.clone.consumer.outbox;

import com.tinder.clone.consumer.kafka.event.MatchCreateEvent;
import com.tinder.clone.consumer.outbox.config.OutboxPublisherProperties;
import com.tinder.clone.consumer.outbox.model.MatchEventOutbox;
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
public class MatchOutboxEventDispatcher {

    private final KafkaTemplate<String, MatchCreateEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxPublisherProperties properties;

    @Value("${app.kafka.topic.match-created}")
    private String matchCreatedTopic;

    public void publish(MatchEventOutbox outboxRow) {
        MatchCreateEvent event = objectMapper.readValue(outboxRow.getPayload(), MatchCreateEvent.class);
        String key = outboxRow.getProfile1Id().toString();

        try {
            kafkaTemplate.send(matchCreatedTopic, key, event)
                    .get(properties.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
            log.debug("Published match outbox row={} eventId={}", outboxRow.getId(), outboxRow.getEventId());
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Throwable rootCause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("Kafka send failed for match event " + outboxRow.getEventId(), rootCause);
        }
    }
}
