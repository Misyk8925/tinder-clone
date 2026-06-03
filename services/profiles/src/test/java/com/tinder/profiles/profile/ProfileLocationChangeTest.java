package com.tinder.profiles.profile;

import com.tinder.contracts.event.v1.ChangeType;
import com.tinder.contracts.event.v1.ProfileUpdatedEvent;
import com.tinder.profiles.location.Location;
import com.tinder.profiles.location.LocationService;
import com.tinder.profiles.location.client.LocationServiceClient;
import com.tinder.profiles.outbox.ProfileOutboxService;
import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.preferences.PreferencesService;
import com.tinder.profiles.profile.cache.DeckPageCacheService;
import com.tinder.profiles.profile.cache.DeckProfileSnapshotCache;
import com.tinder.profiles.profile.cache.ProfileIdentityCacheService;
import com.tinder.profiles.profile.cache.SharedProfileSnapshotCache;
import com.tinder.profiles.profile.dto.profileData.PatchProfileDto;
import com.tinder.profiles.profile.mapper.CreateProfileMapper;
import com.tinder.profiles.profile.mapper.GetProfileMapper;
import com.tinder.profiles.redis.ResilientCacheManager;
import com.tinder.profiles.security.DeckHotPathTokenCache;
import com.tinder.profiles.security.InputSanitizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileLocationChangeTest {

    // -----------------------------------------------------------------------
    // Mocks
    // -----------------------------------------------------------------------
    @Mock private ProfileRepository profileRepository;
    @Mock private ProfileDomainService domainService;
    @Mock private CreateProfileMapper createMapper;
    @Mock private GetProfileMapper getMapper;
    @Mock private ResilientCacheManager resilientCacheManager;
    @Mock private InputSanitizationService sanitizationService;
    @Mock private PreferencesService preferencesService;
    @Mock private ProfileOutboxService profileOutboxService;
    @Mock private LocationService locationService;
    @Mock private LocationServiceClient locationServiceClient;
    @Mock private ProfileIdentityCacheService profileIdentityCacheService;
    @Mock private SharedProfileSnapshotCache sharedProfileSnapshotCache;
    @Mock private DeckProfileSnapshotCache deckProfileSnapshotCache;
    @Mock private DeckPageCacheService deckPageCacheService;
    @Mock private DeckHotPathTokenCache deckHotPathTokenCache;

    private ProfileApplicationService service;

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    // Vienna coordinates (the profile's current location in most tests)
    private static final double VIENNA_LAT = 48.2092;
    private static final double VIENNA_LON = 16.3728;

    // Berlin coordinates (~527 km from Vienna)
    private static final double BERLIN_LAT = 52.5200;
    private static final double BERLIN_LON = 13.4050;

    @BeforeEach
    void setUp() {
        service = new ProfileApplicationService(
                profileRepository, domainService, createMapper, getMapper,
                resilientCacheManager, sanitizationService, preferencesService,
                profileOutboxService, locationService, locationServiceClient,
                profileIdentityCacheService, sharedProfileSnapshotCache,
                deckProfileSnapshotCache, deckPageCacheService, deckHotPathTokenCache
        );
        service.locationChangeThresholdKm = 1.0;
    }

    // -----------------------------------------------------------------------
    // haversineKm — pure formula
    // -----------------------------------------------------------------------

    @Test
    void haversineKm_returnsZeroForIdenticalCoords() {
        double d = ProfileApplicationService.haversineKm(VIENNA_LAT, VIENNA_LON, VIENNA_LAT, VIENNA_LON);
        assertThat(d).isCloseTo(0.0, within(0.001));
    }

    @Test
    void haversineKm_viennaToBerlinIsApprox527km() {
        double d = ProfileApplicationService.haversineKm(VIENNA_LAT, VIENNA_LON, BERLIN_LAT, BERLIN_LON);
        assertThat(d).isCloseTo(527.0, within(5.0));
    }

    @Test
    void haversineKm_isSymmetric() {
        double ab = ProfileApplicationService.haversineKm(VIENNA_LAT, VIENNA_LON, BERLIN_LAT, BERLIN_LON);
        double ba = ProfileApplicationService.haversineKm(BERLIN_LAT, BERLIN_LON, VIENNA_LAT, VIENNA_LON);
        assertThat(ab).isCloseTo(ba, within(0.001));
    }

    @ParameterizedTest(name = "0.01° lat step ≈ 1.11 km")
    @CsvSource("48.0, 16.0, 48.01, 16.0, 1.11")
    void haversineKm_knownShortDistance(double lat1, double lon1, double lat2, double lon2, double expectedKm) {
        double d = ProfileApplicationService.haversineKm(lat1, lon1, lat2, lon2);
        assertThat(d).isCloseTo(expectedKm, within(0.05));
    }

    // -----------------------------------------------------------------------
    // hasMovedSignificantly
    // -----------------------------------------------------------------------

    @Test
    void hasMovedSignificantly_returnsTrueWhenProfileHasNoLocation() {
        Profile profile = new Profile();
        profile.setLocation(null);

        assertThat(service.hasMovedSignificantly(profile, BERLIN_LAT, BERLIN_LON)).isTrue();
    }

    @Test
    void hasMovedSignificantly_returnsTrueWhenMovedBeyondThreshold() {
        Profile profile = profileWithLocation(VIENNA_LAT, VIENNA_LON);

        assertThat(service.hasMovedSignificantly(profile, BERLIN_LAT, BERLIN_LON)).isTrue();
    }

    @Test
    void hasMovedSignificantly_returnsFalseForGpsJitter() {
        Profile profile = profileWithLocation(VIENNA_LAT, VIENNA_LON);
        // Move ~11 metres north — below 1 km threshold
        assertThat(service.hasMovedSignificantly(profile, VIENNA_LAT + 0.0001, VIENNA_LON)).isFalse();
    }

    @Test
    void hasMovedSignificantly_returnsTrueAtExactlyThreshold() {
        Profile profile = profileWithLocation(VIENNA_LAT, VIENNA_LON);
        // ~1.11 km north — just over the 1 km threshold
        assertThat(service.hasMovedSignificantly(profile, VIENNA_LAT + 0.01, VIENNA_LON)).isTrue();
    }

    // -----------------------------------------------------------------------
    // patch() — LOCATION_CHANGE event only fires for significant moves
    // -----------------------------------------------------------------------

    @Test
    void patch_withSignificantCoordMove_firesLocationChangeEvent() {
        Profile profile = profileWithLocation(VIENNA_LAT, VIENNA_LON);
        Location newLocation = locationFor(BERLIN_LAT, BERLIN_LON, "Berlin");

        when(profileRepository.findByUserId("user-1")).thenReturn(profile);
        // city in dto is null → cityForLocation comes from profile.getCity() directly (no sanitize call)
        when(locationServiceClient.resolveFromCoordinates(anyDouble(), anyDouble(), any()))
                .thenReturn(newLocation);
        when(profileRepository.save(any())).thenReturn(profile);

        PatchProfileDto dto = new PatchProfileDto(null, null, null, null, null,
                null, null, BERLIN_LAT, BERLIN_LON);

        service.patch("user-1", dto);

        ArgumentCaptor<ProfileUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(ProfileUpdatedEvent.class);
        verify(profileOutboxService).enqueueProfileUpdated(eventCaptor.capture());
        assertThat(eventCaptor.getValue().changeType()).isEqualTo(ChangeType.LOCATION_CHANGE);
    }

    @Test
    void patch_withGpsJitter_doesNotFireAnyEvent() {
        Profile profile = profileWithLocation(VIENNA_LAT, VIENNA_LON);

        when(profileRepository.findByUserId("user-2")).thenReturn(profile);
        when(profileRepository.save(any())).thenReturn(profile);

        // Move ~11 metres — below threshold
        PatchProfileDto dto = new PatchProfileDto(null, null, null, null, null,
                null, null, VIENNA_LAT + 0.0001, VIENNA_LON);

        service.patch("user-2", dto);

        verify(locationServiceClient, never()).resolveFromCoordinates(anyDouble(), anyDouble(), anyString());
        verify(profileOutboxService, never()).enqueueProfileUpdated(any());
    }

    @Test
    void patch_withGpsJitter_doesNotUpdateLocationEntity() {
        Profile profile = profileWithLocation(VIENNA_LAT, VIENNA_LON);

        when(profileRepository.findByUserId("user-3")).thenReturn(profile);
        when(profileRepository.save(any())).thenReturn(profile);

        PatchProfileDto dto = new PatchProfileDto(null, null, null, null, null,
                null, null, VIENNA_LAT + 0.0001, VIENNA_LON);

        service.patch("user-3", dto);

        verify(locationServiceClient, never()).resolveFromCoordinates(anyDouble(), anyDouble(), anyString());
        verify(locationServiceClient, never()).resolve(anyString());
    }

    @Test
    void patch_withCityChange_firesLocationChangeEventRegardlessOfCoords() {
        Profile profile = profileWithLocation(VIENNA_LAT, VIENNA_LON);
        Location berlinLocation = locationFor(BERLIN_LAT, BERLIN_LON, "Berlin");

        when(profileRepository.findByUserId("user-4")).thenReturn(profile);
        when(sanitizationService.sanitizePlainText("Berlin")).thenReturn("Berlin");
        when(locationServiceClient.resolve("Berlin")).thenReturn(berlinLocation);
        when(profileRepository.save(any())).thenReturn(profile);

        PatchProfileDto dto = new PatchProfileDto(null, null, null, null, "Berlin",
                null, null, null, null);  // coords null — city-only change

        service.patch("user-4", dto);

        ArgumentCaptor<ProfileUpdatedEvent> captor = ArgumentCaptor.forClass(ProfileUpdatedEvent.class);
        verify(profileOutboxService).enqueueProfileUpdated(captor.capture());
        assertThat(captor.getValue().changeType()).isEqualTo(ChangeType.LOCATION_CHANGE);
    }

    @Test
    void patch_firstCoordUpdateOnProfileWithNoLocation_firesLocationChangeEvent() {
        Profile profile = new Profile();
        profile.setProfileId(UUID.randomUUID());
        profile.setLocation(null);
        profile.setPreferences(new Preferences());

        Location newLocation = locationFor(VIENNA_LAT, VIENNA_LON, "Vienna");

        when(profileRepository.findByUserId("user-5")).thenReturn(profile);
        when(sanitizationService.sanitizePlainText("Vienna")).thenReturn("Vienna");
        when(locationServiceClient.resolveFromCoordinates(anyDouble(), anyDouble(), any()))
                .thenReturn(newLocation);
        when(profileRepository.save(any())).thenReturn(profile);

        PatchProfileDto dto = new PatchProfileDto(null, null, null, null, "Vienna",
                null, null, VIENNA_LAT, VIENNA_LON);

        service.patch("user-5", dto);

        ArgumentCaptor<ProfileUpdatedEvent> captor = ArgumentCaptor.forClass(ProfileUpdatedEvent.class);
        verify(profileOutboxService).enqueueProfileUpdated(captor.capture());
        assertThat(captor.getValue().changeType()).isEqualTo(ChangeType.LOCATION_CHANGE);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Profile profileWithLocation(double lat, double lon) {
        var point = GEO.createPoint(new Coordinate(lon, lat));
        point.setSRID(4326);

        Location loc = new Location();
        loc.setGeo(point);
        loc.setCity("Vienna");

        Profile p = new Profile();
        p.setProfileId(UUID.randomUUID());
        p.setCity("Vienna");
        p.setLocation(loc);
        p.setPreferences(new Preferences());
        return p;
    }

    private Location locationFor(double lat, double lon, String city) {
        var point = GEO.createPoint(new Coordinate(lon, lat));
        point.setSRID(4326);

        Location loc = new Location();
        loc.setGeo(point);
        loc.setCity(city);
        return loc;
    }
}
