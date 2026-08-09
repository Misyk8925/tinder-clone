package com.tinder.profiles.infrastructure.persistence.profile.internal;

import com.tinder.contracts.dto.Hobby;
import com.tinder.contracts.dto.SharedPhotoDto;
import com.tinder.profiles.infrastructure.persistence.location.Location;
import com.tinder.profiles.infrastructure.persistence.photos.Photo;
import com.tinder.profiles.infrastructure.persistence.photos.PhotoRepository;
import com.tinder.profiles.infrastructure.persistence.photos.SharedPhotoMapper;
import com.tinder.profiles.infrastructure.persistence.preferences.Preferences;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Exercises the two batch queries that feed the flat shared-profile projection
 * against a real PostGIS database. Their correctness is invisible to a unit test:
 * one is a derived query whose property path Spring Data resolves at bootstrap,
 * the other is native SQL against {@code profile_hobbies}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisplayName("Shared profile association queries")
class SharedProfileAssociationQueriesTest {

    private static final GeometryFactory GEO_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("profiles_query_test")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.liquibase.enabled", () -> "false");
    }

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private EntityManager entityManager;

    private final SharedPhotoMapper sharedPhotoMapper = new SharedPhotoMapper();
    private final SharedProfileRowMapper rowMapper = new SharedProfileRowMapper();

    private UUID adaId;
    private UUID graceId;

    @BeforeEach
    void seed() {
        // One shared Preferences row: the table has a uniqueness constraint on the
        // value combination, which is why production upserts via findOrCreate.
        Preferences preferences = Preferences.builder()
                .minAge(18).maxAge(40).gender("any").maxRange(50)
                .build();
        entityManager.persist(preferences);

        adaId = persistProfile(preferences, "ada", "Ada", List.of(Hobby.HIKING, Hobby.GAMING), 2);
        graceId = persistProfile(preferences, "grace", "Grace", List.of(Hobby.YOGA), 1);
        entityManager.flush();
        entityManager.clear();
    }

    private UUID persistProfile(
            Preferences preferences, String userId, String name, List<Hobby> hobbies, int photoCount) {
        Point geo = GEO_FACTORY.createPoint(new Coordinate(30.52, 50.45));
        geo.setSRID(4326);

        Location location = Location.builder()
                .geo(geo)
                .city("Kyiv")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        entityManager.persist(location);

        ProfileJpaEntity profile = ProfileJpaEntity.builder()
                .userId(userId)
                .name(name)
                .age(29)
                .gender("FEMALE")
                .bio("bio of " + name)
                .city("Kyiv")
                .isActive(true)
                .location(location)
                .preferences(preferences)
                .hobbies(new ArrayList<>(hobbies))
                .photos(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();
        entityManager.persist(profile);

        for (int position = 0; position < photoCount; position++) {
            Photo photo = new Photo();
            photo.setProfile(profile);
            photo.setS3Key(userId + "/photo-" + position);
            photo.setPrimary(position == 0);
            photo.setPosition(position);
            photo.setUrl("https://cdn/" + userId + "/" + position);
            photo.setContentType("image/jpeg");
            photo.setSize(2048L);
            photo.setCreatedAt(LocalDateTime.now());
            entityManager.persist(photo);
        }

        return profile.getProfileId();
    }

    @Test
    @DisplayName("the derived photo query batch-loads photos for several profiles at once")
    void batchLoadsPhotosByProfileIds() {
        List<Photo> photos = photoRepository.findAllByProfile_ProfileIdIn(List.of(adaId, graceId));

        then(photos).hasSize(3);

        Map<UUID, List<SharedPhotoDto>> byProfileId = sharedPhotoMapper.byProfileId(photos);
        then(byProfileId.get(adaId)).hasSize(2);
        then(byProfileId.get(graceId)).hasSize(1);
        then(byProfileId.get(adaId)).extracting(SharedPhotoDto::position).containsExactly(0, 1);
        then(byProfileId.get(adaId)).allSatisfy(photo -> then(photo.profileId()).isEqualTo(adaId));
    }

    @Test
    @DisplayName("the native hobby query returns (profile_id, hobby) rows that group per profile")
    void batchLoadsHobbiesByProfileIds() {
        List<Object[]> hobbyRows = profileRepository.findHobbyRowsByProfileIds(List.of(adaId, graceId));

        then(hobbyRows).hasSize(3);

        Map<UUID, List<Hobby>> byProfileId = rowMapper.hobbiesByProfileId(hobbyRows);
        then(byProfileId.get(adaId)).containsExactlyInAnyOrder(Hobby.HIKING, Hobby.GAMING);
        then(byProfileId.get(graceId)).containsExactly(Hobby.YOGA);
    }

    @Test
    @DisplayName("the flat projection plus both batch queries reconstructs a complete snapshot")
    void projectionPlusAssociationsIsComplete() {
        List<Object[]> rows = profileRepository.findSharedProfileRowsByIds(List.of(adaId, graceId));
        List<UUID> ids = rowMapper.idsOf(rows);

        var dtos = rowMapper.toDtos(
                rows,
                sharedPhotoMapper.byProfileId(photoRepository.findAllByProfile_ProfileIdIn(ids)),
                rowMapper.hobbiesByProfileId(profileRepository.findHobbyRowsByProfileIds(ids)));

        then(dtos).hasSize(2);
        then(dtos).allSatisfy(dto -> {
            then(dto.photos()).isNotEmpty();
            then(dto.hobbies()).isNotEmpty();
            then(dto.location()).isNotNull();
            then(dto.location().latitude()).isEqualTo(50.45);
            then(dto.preferences()).isNotNull();
        });
    }
}
