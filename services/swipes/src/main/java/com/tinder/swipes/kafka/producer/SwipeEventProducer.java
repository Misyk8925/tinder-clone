package com.tinder.swipes.kafka.producer;

import com.tinder.swipes.kafka.SwipeCreatedEvent;
import com.tinder.swipes.model.dto.SwipeRecordDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class SwipeEventProducer {

    private final KafkaTemplate<String, SwipeCreatedEvent> kafkaTemplate;

    public void sendSwipeEvent(SwipeCreatedEvent event, String key, String topic) {

        log.info("Sending swipe event to topic: {} with key: {} and event: {}", topic, key, event);

        CompletableFuture<SendResult<String, SwipeCreatedEvent>> future =
                kafkaTemplate.send(topic, key, event);
        future.whenComplete((result, ex) -> {
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
