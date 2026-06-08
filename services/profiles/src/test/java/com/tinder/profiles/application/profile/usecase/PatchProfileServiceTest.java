package com.tinder.profiles.application.profile.usecase;

import com.tinder.contracts.event.v1.ChangeType;
import com.tinder.profiles.application.profile.command.PatchProfileCommand;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Characterizes the patch write path: GPS-jitter suppression and the
 * LOCATION_CHANGE classification. The Haversine math itself lives in (and is
 * tested via) the domain {@code GeoPoint}/{@code Profile} types.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PatchProfileService")
class PatchProfileServiceTest {

    private static final double VIENNA_LAT = 48.2092;
    private static final double VIENNA_LON = 16.3728;
    private static final double BERLIN_LAT = 52.5200;
    private static final double BERLIN_LON = 13.4050;

    @Mock private ProfileRepositoryPort profiles;
    @Mock private LocationPort locations;
    @Mock private DomainEventPublisherPort events;
    @Mock private ProfileCachePort cache;

    private PatchProfileService service;

    @BeforeEach
    void setUp() {
        service = new PatchProfileService(profiles, locations, events, cache, new ProfileDomainService());
        service.locationChangeThresholdKm = 1.0;
    }

    private Profile viennaProfile(String userId) {
        return Profile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Alice").age(30).gender("FEMALE").city("Vienna")
                .position(new GeoPoint(VIENNA_LAT, VIENNA_LON))
                .preferences(new MatchingPreferences(18, 99, "all", 50))
                .build();
    }

    private PatchProfileCommand coordsPatch(String userId, double lat, double lon) {
        return new PatchProfileCommand(userId, null, null, null, null, null, null, null, lat, lon);
    }

    @Test
    @DisplayName("a significant coordinate move publishes a LOCATION_CHANGE event")
    void significantMovePublishesLocationChange() {
        Profile profile = viennaProfile("user-1");
        given(profiles.findByUserId("user-1")).willReturn(Optional.of(profile));
        given(profiles.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(locations.resolve(anyDouble(), anyDouble(), any()))
                .willReturn(new ResolvedLocation(new GeoPoint(BERLIN_LAT, BERLIN_LON), "Berlin"));

        service.handle(coordsPatch("user-1", BERLIN_LAT, BERLIN_LON));

        ArgumentCaptor<ChangeType> type = ArgumentCaptor.forClass(ChangeType.class);
        verify(events).publishUpdated(any(UUID.class), type.capture(), any());
        assertThat(type.getValue()).isEqualTo(ChangeType.LOCATION_CHANGE);
    }

    @Test
    @DisplayName("GPS jitter below the threshold resolves nothing and publishes no event")
    void gpsJitterSuppressed() {
        Profile profile = viennaProfile("user-2");
        given(profiles.findByUserId("user-2")).willReturn(Optional.of(profile));
        given(profiles.save(any())).willAnswer(inv -> inv.getArgument(0));

        // ~11 metres north — below the 1 km threshold
        service.handle(coordsPatch("user-2", VIENNA_LAT + 0.0001, VIENNA_LON));

        verify(locations, never()).resolve(anyDouble(), anyDouble(), anyString());
        verify(events, never()).publishUpdated(any(), any(), any());
    }

    @Test
    @DisplayName("a city change publishes LOCATION_CHANGE even without coordinates")
    void cityChangePublishesLocationChange() {
        Profile profile = viennaProfile("user-4");
        given(profiles.findByUserId("user-4")).willReturn(Optional.of(profile));
        given(profiles.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(locations.resolve(null, null, "Berlin"))
                .willReturn(new ResolvedLocation(new GeoPoint(BERLIN_LAT, BERLIN_LON), "Berlin"));

        PatchProfileCommand cmd = new PatchProfileCommand(
                "user-4", null, null, null, null, "Berlin", null, null, null, null);
        service.handle(cmd);

        ArgumentCaptor<ChangeType> type = ArgumentCaptor.forClass(ChangeType.class);
        verify(events).publishUpdated(any(UUID.class), type.capture(), any());
        assertThat(type.getValue()).isEqualTo(ChangeType.LOCATION_CHANGE);
    }

    @Test
    @DisplayName("the first coordinate update on a profile with no position publishes LOCATION_CHANGE")
    void firstCoordinateUpdatePublishesLocationChange() {
        Profile profile = Profile.builder()
                .id(UUID.randomUUID()).userId("user-5")
                .name("Bob").age(25).gender("MALE")
                .preferences(new MatchingPreferences(18, 99, "all", 50))
                .build(); // no city, no position
        given(profiles.findByUserId("user-5")).willReturn(Optional.of(profile));
        given(profiles.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(locations.resolve(any(), any(), any()))
                .willReturn(new ResolvedLocation(new GeoPoint(VIENNA_LAT, VIENNA_LON), "Vienna"));

        PatchProfileCommand cmd = new PatchProfileCommand(
                "user-5", null, null, null, null, "Vienna", null, null, VIENNA_LAT, VIENNA_LON);
        service.handle(cmd);

        ArgumentCaptor<ChangeType> type = ArgumentCaptor.forClass(ChangeType.class);
        verify(events).publishUpdated(any(UUID.class), type.capture(), any());
        assertThat(type.getValue()).isEqualTo(ChangeType.LOCATION_CHANGE);
    }
}
