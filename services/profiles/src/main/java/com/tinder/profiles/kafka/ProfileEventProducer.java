package com.tinder.profiles.kafka;

import com.tinder.profiles.kafka.dto.ProfileCreateEvent;
import com.tinder.profiles.kafka.dto.ProfileDeleteEvent;
import com.tinder.profiles.kafka.dto.ProfileUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProfileEventProducer {

    private final KafkaTemplate<String, ProfileUpdatedEvent> profileUpdatedEventKafkaTemplate;
    private final KafkaTemplate<String, ProfileDeleteEvent> profileDeleteEventKafkaTemplate;
    private final KafkaTemplate<String, ProfileCreateEvent> profileCreateEventKafkaTemplate;

    public void sendProfileUpdateEvent(
            ProfileUpdatedEvent event,
            String key,
            String topic
    ) {
        log.info("Sending profile event to topic: {} with key: {} and event: {}", topic, key, event);

        profileUpdatedEventKafkaTemplate.send(topic, key, event).whenComplete(
                (result, ex) -> {
            if (ex == null) {
                log.info("Update event sent successfully: topic={}, partition={}, offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send event to topic {}: {}", topic, ex.getMessage(), ex);
            }
        });
    }

    public void sendProfileDeleteEvent(
           ProfileDeleteEvent event,
           String key,
           String topic
    ){
        log.info("Sending profile delete event to topic: {} with key: {} and event: {}", topic, key, event);

        profileDeleteEventKafkaTemplate.send(topic, key, event).whenComplete(
                (result, ex) -> {
            if (ex == null) {
                log.info("Delete event sent successfully: topic={}, partition={}, offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send event to topic {}: {}", topic, ex.getMessage(), ex);
            }
        });
    }

    public void sendProfileCreateEvent(
           ProfileCreateEvent event,
           String key,
           String topic
    ){
        log.info("Sending profile create event to topic: {} with key: {} and event: {}", topic, key, event);

        profileCreateEventKafkaTemplate.send(topic, key, event).whenComplete(
                (result, ex) -> {
            if (ex == null) {
                log.info("Create event sent successfully: topic={}, partition={}, offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send event to topic {}: {}", topic, ex.getMessage(), ex);
            }
        });
    }
}