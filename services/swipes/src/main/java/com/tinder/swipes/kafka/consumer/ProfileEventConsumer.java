package com.tinder.swipes.kafka.consumer;

import com.tinder.swipes.kafka.ProfileCreateEvent;
import com.tinder.swipes.kafka.ProfileDeleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileEventConsumer {

    private final ConsumerService consumerService;



    @KafkaListener(
            topics = "${app.kafka.topic.profile-created}",
            groupId = "${spring.kafka.consumer.group-id}-profile",
            containerFactory = "profileKafkaListenerContainerFactory"
    )
    public void handleProfileCreatedEvent(
            @Payload ProfileCreateEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
            ) {
        log.info("Received ProfileCreatedEvent from partition: {}, offset: {}, event: {}",
                partition, offset, event);

        try {

            log.info("Processing ProfileCreatedEvent: {}", event);
            consumerService.saveProfileCache(event);

            // Acknowledge the message after successful processing
            acknowledgment.acknowledge();
            log.info("Acknowledged ProfileCreatedEvent: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Error processing ProfileCreatedEvent: {}", event, e);
            // Optionally, implement retry logic or dead-letter queue handling here
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topic.profile-deleted}",
            groupId = "${spring.kafka.consumer.group-id}-profile-delete",
            containerFactory = "profileDeleteKafkaListenerContainerFactory"
    )
    public void handleProfileDeletedEvent(
            @Payload ProfileDeleteEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("Received ProfileDeleteEvent from partition: {}, offset: {}, profileId: {}",
                partition, offset, event.getProfileId());

        try {
            log.info("Processing ProfileDeleteEvent: eventId={}, profileId={}",
                    event.getEventId(), event.getProfileId());

            consumerService.deleteProfileCache(event);

            // Acknowledge the message after successful processing
            acknowledgment.acknowledge();
            log.info("Acknowledged ProfileDeleteEvent: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Error processing ProfileDeleteEvent: eventId={}, profileId={}",
                    event.getEventId(), event.getProfileId(), e);
            // Acknowledge to prevent infinite retry
            acknowledgment.acknowledge();
        }
    }
}
