package com.tinder.clone.consumer.service;

import com.tinder.clone.consumer.AbstractIntegrationTest;
import com.tinder.clone.consumer.kafka.event.ProfileCreateEvent;
import com.tinder.clone.consumer.kafka.event.ProfileDeleteEvent;
import com.tinder.clone.consumer.model.ProfileCacheModel;
import com.tinder.clone.consumer.repository.ProfileCacheRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ProfileEventService}.
 * Uses real PostgreSQL via Testcontainers to verify cache persistence and deletion.
 */
class ProfileEventServiceIntegrationTest extends AbstractIntegrationTest {

    // Prevent Kafka listeners from starting against the dummy broker
    @MockitoBean
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private ProfileEventService profileEventService;

    @Autowired
    private ProfileCacheRepository profileCacheRepository;

    @AfterEach
    void tearDown() {
        profileCacheRepository.deleteAll();
    }

    @Test
    void saveProfileCache_persistsProfileCacheModel_withCorrectTimestamp() {
        UUID profileId = UUID.randomUUID();
        Instant timestamp = Instant.now();

        profileEventService.saveProfileCache(ProfileCreateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(profileId)
                .timestamp(timestamp)
                .build());

        Optional<ProfileCacheModel> saved = profileCacheRepository.findById(profileId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getProfileId()).isEqualTo(profileId);
        assertThat(saved.get().getCreatedAt()).isEqualTo(timestamp);
    }

    @Test
    void saveProfileCache_usesCurrentTime_whenTimestampIsNull() {
        UUID profileId = UUID.randomUUID();
        Instant before = Instant.now();

        profileEventService.saveProfileCache(ProfileCreateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(profileId)
                .timestamp(null)
                .build());

        Optional<ProfileCacheModel> saved = profileCacheRepository.findById(profileId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getCreatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void deleteProfileCache_removesExistingRecord() {
        UUID profileId = UUID.randomUUID();
        profileCacheRepository.save(ProfileCacheModel.builder()
                .profileId(profileId)
                .createdAt(Instant.now())
                .build());

        profileEventService.deleteProfileCache(ProfileDeleteEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(profileId)
                .timestamp(Instant.now())
                .build());

        assertThat(profileCacheRepository.findById(profileId)).isEmpty();
    }

    @Test
    void deleteProfileCache_doesNotThrow_whenProfileNotFound() {
        UUID nonExistentId = UUID.randomUUID();

        // Must not throw — just log a warning
        profileEventService.deleteProfileCache(ProfileDeleteEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(nonExistentId)
                .timestamp(Instant.now())
                .build());

        assertThat(profileCacheRepository.findById(nonExistentId)).isEmpty();
    }

    @Test
    void saveProfileCache_isIdempotent_savingSameTwiceKeepsSingleRecord() {
        UUID profileId = UUID.randomUUID();

        profileEventService.saveProfileCache(ProfileCreateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(profileId)
                .timestamp(Instant.now())
                .build());

        profileEventService.saveProfileCache(ProfileCreateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(profileId)
                .timestamp(Instant.now().plusSeconds(60))
                .build());

        assertThat(profileCacheRepository.count()).isEqualTo(1);
    }
}

