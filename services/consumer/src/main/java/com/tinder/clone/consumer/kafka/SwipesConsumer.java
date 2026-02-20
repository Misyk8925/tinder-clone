package com.tinder.clone.consumer.kafka;

import com.tinder.clone.consumer.kafka.event.SwipeCreatedEvent;
import com.tinder.clone.consumer.service.SwipeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SwipesConsumer {


    private final SwipeService consumerService;

    @KafkaListener(
            topics = "${app.kafka.topic.swipe-created}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleSwipeCreatedEvent(
            @Payload SwipeCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("Received SwipeCreatedEvent from partition: {}, offset: {}, event: {}",
                partition, offset, event);

        try {

            log.info("Processing SwipeCreatedEvent: {}", event);
            consumerService.save(event);

            // Acknowledge the message after successful processing
            acknowledgment.acknowledge();
            log.info("Acknowledged SwipeCreatedEvent: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Error processing SwipeCreatedEvent: {}", event, e);
            // Optionally, implement retry logic or dead-letter queue handling here
        }
    }
}
