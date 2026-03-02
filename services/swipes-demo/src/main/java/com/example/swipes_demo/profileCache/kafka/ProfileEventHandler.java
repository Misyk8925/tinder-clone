package com.example.swipes_demo.profileCache.kafka;

import com.example.swipes_demo.profileCache.*;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileEventHandler {

    private final ProfileCacheService cacheService;

    @KafkaListener(
            topics = "${kafka.topics.profile-created}",
            groupId = "swipe-service",
            containerFactory = "profileCreateEventKafkaListenerContainerFactory"
    )
    public void handleCreateProfileEvent(
            @Payload ProfileCreateEvent event
    ) {

        try {
            cacheService.saveProfileCache(event);
        } catch (Exception e) {
            log.error("Error processing ProfileCreatedEvent: {}", event, e);
        }
    }

    @KafkaListener(
            topics = "${kafka.topics.profile-deleted}",
            groupId = "swipe-service",
            containerFactory = "profileDeleteEventKafkaListenerContainerFactory"
    )
    public void handleDeleteProfileEvent(
            @Payload ProfileDeleteEvent event
    ) {

        try {
            cacheService.deleteProfileCache(event);
        } catch (Exception e) {
            log.error("Error processing ProfileDeleteEvent: {}", event, e);
        }
    }

}
