package com.tinder.profiles.application.profile.usecase;

import com.tinder.contracts.dto.Hobby;
import com.tinder.profiles.application.profile.command.CreateProfileCommand;
import com.tinder.profiles.application.profile.command.DeleteProfileCommand;
import com.tinder.profiles.application.profile.port.out.DomainEventPublisherPort;
import com.tinder.profiles.application.profile.port.out.ProfileCachePort;
import com.tinder.profiles.application.profile.port.out.ProfileRepositoryPort;
import com.tinder.profiles.application.profile.port.out.ResolvedLocation;
import com.tinder.profiles.application.profile.port.out.LocationPort;
import com.tinder.profiles.domain.profile.GeoPoint;
import com.tinder.profiles.domain.profile.MatchingPreferences;
import com.tinder.profiles.domain.profile.Profile;
import com.tinder.profiles.domain.profile.ProfileDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/** Verifies the create/delete use cases publish the expected domain events via the outbox port. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Write use cases — outbox/event publishing")
class WriteUseCaseOutboxTest {

    @Mock private ProfileRepositoryPort profiles;
    @Mock private LocationPort locations;
    @Mock private DomainEventPublisherPort events;
    @Mock private ProfileCachePort cache;

    private CreateProfileService createService;
    private DeleteProfileService deleteService;

    @BeforeEach
    void setUp() {
        createService = new CreateProfileService(profiles, locations, events, cache, new ProfileDomainService());
        deleteService = new DeleteProfileService(profiles, cache, events);
    }

    private CreateProfileCommand createCommand() {
        return new CreateProfileCommand("user-1", "Alice", 29, "female", "bio", "Berlin",
                new MatchingPreferences(24, 40, "male", 30),
                List.of(Hobby.HIKING, Hobby.PHOTOGRAPHY, Hobby.GAMING), null, null);
    }

    @Test
    @DisplayName("create publishes a created event for the saved profile id")
    void createPublishesEvent() {
        UUID savedId = UUID.randomUUID();
        given(profiles.findByUserId("user-1")).willReturn(Optional.empty());
        given(locations.resolve(null, null, "Berlin"))
                .willReturn(new ResolvedLocation(new GeoPoint(52.52, 13.40), "Berlin"));
        given(profiles.save(any())).willReturn(
                Profile.builder().id(savedId).userId("user-1").name("Alice").city("Berlin").build());

        UUID result = createService.handle(createCommand());

        assertThat(result).isEqualTo(savedId);
        verify(events).publishCreated(savedId, "user-1");
    }

    @Test
    @DisplayName("create propagates an outbox/event publish failure")
    void createPropagatesPublishFailure() {
        UUID savedId = UUID.randomUUID();
        given(profiles.findByUserId("user-2")).willReturn(Optional.empty());
        given(locations.resolve(null, null, "Berlin"))
                .willReturn(new ResolvedLocation(new GeoPoint(52.52, 13.40), "Berlin"));
        given(profiles.save(any())).willReturn(
                Profile.builder().id(savedId).userId("user-2").name("Alice").city("Berlin").build());
        doThrow(new IllegalStateException("outbox insert failed"))
                .when(events).publishCreated(any(), any());

        CreateProfileCommand cmd = new CreateProfileCommand("user-2", "Alice", 29, "female", "bio", "Berlin",
                new MatchingPreferences(24, 40, "male", 30), List.of(Hobby.HIKING), null, null);

        assertThatThrownBy(() -> createService.handle(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outbox insert failed");
    }

    @Test
    @DisplayName("delete soft-deletes, evicts caches and publishes a deleted event")
    void deletePublishesEventAndEvicts() {
        UUID profileId = UUID.randomUUID();
        Profile profile = Profile.builder()
                .id(profileId).userId("user-3").name("Alice").city("Berlin")
                .active(true).deleted(false).build();
        given(profiles.findByUserId("user-3")).willReturn(Optional.of(profile));
        given(profiles.save(any())).willReturn(profile);

        deleteService.handle(new DeleteProfileCommand("user-3"));

        verify(events).publishDeleted(profileId);
        verify(cache).evict(profileId);
        verify(cache).evictReadModels("user-3", profileId);
    }
}
