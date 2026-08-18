package com.example.swipes_demo.profileCache.kafka;

import com.example.swipes_demo.profileCache.ProfileCacheService;
import com.example.swipes_demo.profileCache.ProfileDeleteEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ProfileEventHandlerTest {

    private final ProfileCacheService cacheService = mock(ProfileCacheService.class);
    private final ProfileEventHandler handler = new ProfileEventHandler(cacheService);

    @Test
    void givenProfileCreateCacheWriteFails_thenFailureReachesKafkaErrorHandler() {
        ProfileCreateEvent event = new ProfileCreateEvent(
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "user-1");
        doThrow(new IllegalStateException("cache unavailable"))
                .when(cacheService).saveProfileCache(event);

        assertThatThrownBy(() -> handler.handleCreateProfileEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cache unavailable");
    }

    @Test
    void givenProfileDeleteCacheWriteFails_thenFailureReachesKafkaErrorHandler() {
        ProfileDeleteEvent event = new ProfileDeleteEvent(
                UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        doThrow(new IllegalStateException("cache unavailable"))
                .when(cacheService).deleteProfileCache(event);

        assertThatThrownBy(() -> handler.handleDeleteProfileEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cache unavailable");
    }
}
