package com.example.swipes_demo.profileCache;

import com.example.swipes_demo.profileCache.client.ProfileServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileCacheKafkaDeliveryTest {

    @Test
    void redisEvictionFailurePropagatesSoKafkaDoesNotCommitProfileDeletion() {
        UUID profileId = UUID.randomUUID();
        ProfileCache profile = ProfileCache.builder()
                .profileId(profileId)
                .userId("user-1")
                .createdAt(Instant.now())
                .build();
        ProfileCacheRepository repository = mock(ProfileCacheRepository.class);
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveSetOperations<String, String> setOperations = mock(ReactiveSetOperations.class);
        when(repository.findById(profileId)).thenReturn(Optional.of(profile));
        when(redis.opsForSet()).thenReturn(setOperations);
        when(setOperations.remove("profiles:exists", profileId.toString()))
                .thenReturn(Mono.error(new IllegalStateException("redis unavailable")));

        ProfileCacheService service = new ProfileCacheService(
                repository,
                redis,
                mock(ProfileServiceClient.class)
        );
        ProfileDeleteEvent event = new ProfileDeleteEvent(UUID.randomUUID(), profileId, Instant.now());

        assertThatThrownBy(() -> service.deleteProfileCache(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis unavailable");
        verify(repository).delete(profile);
    }
}
