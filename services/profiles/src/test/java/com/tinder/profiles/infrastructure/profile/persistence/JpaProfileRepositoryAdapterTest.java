package com.tinder.profiles.infrastructure.profile.persistence;

import com.tinder.profiles.domain.profile.GeoPoint;
import com.tinder.profiles.domain.profile.MatchingPreferences;
import com.tinder.profiles.domain.profile.Profile;
import com.tinder.profiles.infrastructure.persistence.location.Location;
import com.tinder.profiles.infrastructure.persistence.location.LocationRepository;
import com.tinder.profiles.infrastructure.persistence.preferences.Preferences;
import com.tinder.profiles.infrastructure.persistence.preferences.PreferencesDto;
import com.tinder.profiles.infrastructure.persistence.preferences.PreferencesService;
import com.tinder.profiles.profile.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaProfileRepositoryAdapter")
class JpaProfileRepositoryAdapterTest {

    private static final GeometryFactory GEO_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private PreferencesService preferencesService;

    private JpaProfileRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaProfileRepositoryAdapter(
                profileRepository, new ProfilePersistenceMapper(), locationRepository, preferencesService);
    }

    private static Point point(double lat, double lon) {
        Point p = GEO_FACTORY.createPoint(new Coordinate(lon, lat));
        p.setSRID(4326);
        return p;
    }

    private static Location viennaLocation() {
        return Location.builder().city("Vienna").geo(point(48.2, 16.37)).build();
    }

    private static Preferences preferences() {
        return Preferences.builder().minAge(18).maxAge(40).gender("MALE").maxRange(50).build();
    }

    private static Profile domainProfile(UUID id) {
        return Profile.builder()
                .id(id)
                .userId("user-1")
                .name("Alice")
                .age(30)
                .gender("FEMALE")
                .bio("hi")
                .city("Vienna")
                .position(new GeoPoint(48.2, 16.37))
                .active(true)
                .preferences(new MatchingPreferences(18, 40, "MALE", 50))
                .build();
    }

    @Test
    @DisplayName("save resolves the Location and Preferences FKs and persists the scalar state")
    void saveReconcilesFks() {
        // given a brand-new aggregate (no id → insert path)
        given(locationRepository.findByCity("Vienna")).willReturn(Optional.of(viennaLocation()));
        given(preferencesService.findOrCreate(any(PreferencesDto.class))).willReturn(preferences());
        given(profileRepository.save(any(com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        Profile saved = adapter.save(domainProfile(null));

        // then the entity handed to the repository carries the reconciled state
        ArgumentCaptor<com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity> captor =
                ArgumentCaptor.forClass(com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity.class);
        verify(profileRepository).save(captor.capture());
        com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity entity = captor.getValue();
        then(entity.getName()).isEqualTo("Alice");
        then(entity.getCity()).isEqualTo("Vienna");
        then(entity.getLocation().getCity()).isEqualTo("Vienna");
        then(entity.getPreferences()).isNotNull();
        then(entity.isActive()).isTrue();

        // and a domain aggregate is mapped back out
        then(saved.getName()).isEqualTo("Alice");
        then(saved.getPosition()).isEqualTo(new GeoPoint(48.2, 16.37));

        ArgumentCaptor<PreferencesDto> prefsCaptor = ArgumentCaptor.forClass(PreferencesDto.class);
        verify(preferencesService).findOrCreate(prefsCaptor.capture());
        then(prefsCaptor.getValue().getMinAge()).isEqualTo(18);
        then(prefsCaptor.getValue().getMaxRange()).isEqualTo(50);
    }

    @Test
    @DisplayName("save loads the existing row on the update path to preserve persistence-only state")
    void saveUpdatePathLoadsExistingRow() {
        // given an existing entity with a version and createdAt that must survive
        UUID id = UUID.randomUUID();
        LocalDateTime created = LocalDateTime.now().minusDays(5);
        com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity existing = com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity.builder()
                .profileId(id)
                .userId("user-1")
                .name("OldName")
                .city("Vienna")
                .location(viennaLocation())
                .preferences(preferences())
                .version(7L)
                .createdAt(created)
                .build();
        given(profileRepository.findById(id)).willReturn(Optional.of(existing));
        given(locationRepository.findByCity("Vienna")).willReturn(Optional.of(viennaLocation()));
        given(preferencesService.findOrCreate(any(PreferencesDto.class))).willReturn(preferences());
        given(profileRepository.save(any(com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        adapter.save(domainProfile(id));

        // then the same managed entity is saved with its version/createdAt intact
        ArgumentCaptor<com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity> captor =
                ArgumentCaptor.forClass(com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity.class);
        verify(profileRepository).save(captor.capture());
        com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity entity = captor.getValue();
        then(entity).isSameAs(existing);
        then(entity.getVersion()).isEqualTo(7L);
        then(entity.getCreatedAt()).isEqualTo(created);
        then(entity.getName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("save fails fast when the Location for the city has not been resolved")
    void saveFailsWhenLocationMissing() {
        given(locationRepository.findByCity("Vienna")).willReturn(Optional.empty());

        thenThrownBy(() -> adapter.save(domainProfile(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vienna");
    }

    @Test
    @DisplayName("findByUserId returns empty when the repository has no row")
    void findByUserIdEmpty() {
        given(profileRepository.findByUserId("missing")).willReturn(null);
        then(adapter.findByUserId("missing")).isEmpty();
    }

    @Test
    @DisplayName("findByUserId maps the row to the domain aggregate when present")
    void findByUserIdMaps() {
        com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity entity = com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity.builder()
                .profileId(UUID.randomUUID())
                .userId("user-1")
                .name("Alice")
                .city("Vienna")
                .location(viennaLocation())
                .preferences(preferences())
                .build();
        given(profileRepository.findByUserId("user-1")).willReturn(entity);

        Optional<Profile> result = adapter.findByUserId("user-1");

        then(result).isPresent();
        then(result.get().getName()).isEqualTo("Alice");
        then(result.get().getPosition()).isEqualTo(new GeoPoint(48.2, 16.37));
    }

    @Test
    @DisplayName("findActiveProfileIdByUserId wraps the nullable repository result")
    void findActiveProfileId() {
        UUID id = UUID.randomUUID();
        given(profileRepository.findActiveProfileIdByUserId("user-1")).willReturn(id);
        then(adapter.findActiveProfileIdByUserId("user-1")).contains(id);

        given(profileRepository.findActiveProfileIdByUserId("none")).willReturn(null);
        then(adapter.findActiveProfileIdByUserId("none")).isEmpty();
    }
}
