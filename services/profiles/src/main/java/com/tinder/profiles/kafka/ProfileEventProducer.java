package com.tinder.profiles.kafka;

import com.tinder.profiles.kafka.dto.ProfileUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProfileEventProducer {

    private final KafkaTemplate<String, ProfileUpdatedEvent> kafkaTemplate;

    public void sendProfileUpdateEvent(
            ProfileUpdatedEvent event,
            String key,
            String topic
    ) {
        log.info("Sending profile event to topic: {} with key: {} and event: {}", topic, key, event);

        kafkaTemplate.send(topic, key, event).whenComplete(
                (result, ex) -> {
            if (ex == null) {
                log.info("Event sent successfully: topic={}, partition={}, offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send event to topic {}: {}", topic, ex.getMessage(), ex);
            }
        });
    }
}
