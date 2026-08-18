package com.example.swipes_demo.profileCache.kafka;

import com.example.swipes_demo.profileCache.*;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
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
        cacheService.saveProfileCache(event);
    }

    @KafkaListener(
            topics = "${kafka.topics.profile-deleted}",
            groupId = "swipe-service",
            containerFactory = "profileDeleteEventKafkaListenerContainerFactory"
    )
    public void handleDeleteProfileEvent(
            @Payload ProfileDeleteEvent event
    ) {
        cacheService.deleteProfileCache(event);
    }

}
