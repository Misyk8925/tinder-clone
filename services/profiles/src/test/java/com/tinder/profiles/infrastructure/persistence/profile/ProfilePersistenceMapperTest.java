package com.tinder.profiles.infrastructure.persistence.profile;

import com.tinder.profiles.domain.profile.GeoPoint;
import com.tinder.profiles.domain.profile.Hobby;
import com.tinder.profiles.domain.profile.MatchingPreferences;
import com.tinder.profiles.domain.profile.Profile;
import com.tinder.profiles.infrastructure.persistence.location.Location;
import com.tinder.profiles.infrastructure.persistence.preferences.Preferences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;

@DisplayName("ProfilePersistenceMapper")
class ProfilePersistenceMapperTest {

    private static final GeometryFactory GEO_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final ProfilePersistenceMapper mapper = new ProfilePersistenceMapper();

    private static Point point(double lat, double lon) {
        Point p = GEO_FACTORY.createPoint(new Coordinate(lon, lat));
        p.setSRID(4326);
        return p;
    }

    private static Preferences preferences() {
        return Preferences.builder().minAge(18).maxAge(40).gender("MALE").maxRange(50).build();
    }

    @Nested
    @DisplayName("when mapping a JPA entity to the domain aggregate")
    class ToDomain {

        @Test
        @DisplayName("copies every mapped field and derives the value objects")
        void mapsAllFields() {
            // given
            UUID id = UUID.randomUUID();
            LocalDateTime created = LocalDateTime.now().minusDays(3);
            Location location = Location.builder().city("Vienna").geo(point(48.2, 16.37)).build();
            com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity entity = com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity.builder()
                    .profileId(id)
                    .userId("user-1")
                    .name("Alice")
                    .age(30)
                    .gender("FEMALE")
                    .bio("hi")
                    .city("Vienna")
                    .location(location)
                    .isActive(true)
                    .isPremium(true)
                    .premiumExpiresAt(created.plusMonths(1))
                    .isDeleted(false)
                    .preferences(preferences())
                    .hobbies(List.of(com.tinder.contracts.dto.Hobby.HIKING))
                    .createdAt(created)
                    .build();

            // when
            Profile domain = mapper.toDomain(entity);

            // then
            then(domain.getId()).isEqualTo(id);
            then(domain.getUserId()).isEqualTo("user-1");
            then(domain.getName()).isEqualTo("Alice");
            then(domain.getAge()).isEqualTo(30);
            then(domain.getGender()).isEqualTo("FEMALE");
            then(domain.getBio()).isEqualTo("hi");
            then(domain.getCity()).isEqualTo("Vienna");
            then(domain.getPosition()).isEqualTo(new GeoPoint(48.2, 16.37));
            then(domain.isActive()).isTrue();
            then(domain.isPremium()).isTrue();
            then(domain.getPremiumExpiresAt()).isEqualTo(created.plusMonths(1));
            then(domain.isDeleted()).isFalse();
            then(domain.getPreferences()).isEqualTo(new MatchingPreferences(18, 40, "MALE", 50));
            then(domain.getHobbies()).containsExactly(Hobby.HIKING);
            then(domain.getCreatedAt()).isEqualTo(created);
        }

        @Test
        @DisplayName("yields a null position when the entity has no location")
        void nullLocationYieldsNullPosition() {
            // given
            com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity entity = com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity.builder()
                    .profileId(UUID.randomUUID())
                    .userId("user-1")
                    .name("Alice")
                    .city("Vienna")
                    .preferences(preferences())
                    .build();

            // when
            Profile domain = mapper.toDomain(entity);

            // then
            then(domain.getPosition()).isNull();
            then(domain.currentPosition()).isEmpty();
        }

        @Test
        @DisplayName("returns null for a null entity")
        void nullEntity() {
            then(mapper.toDomain(null)).isNull();
        }
    }

    @Nested
    @DisplayName("when applying the aggregate onto a JPA entity")
    class ApplyTo {

        @Test
        @DisplayName("copies scalar state and the supplied FK rows")
        void copiesScalarsAndFks() {
            // given an aggregate and a fresh entity plus resolved FK rows
            Profile domain = Profile.builder()
                    .id(UUID.randomUUID())
                    .userId("user-9")
                    .name("Bob")
                    .age(28)
                    .gender("MALE")
                    .bio("bio")
                    .city("Berlin")
                    .position(new GeoPoint(52.52, 13.40))
                    .active(true)
                    .premium(false)
                    .hobbies(List.of(Hobby.COOKING))
                    .build();
            com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity entity = new com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity();
            Location location = Location.builder().city("Berlin").geo(point(52.52, 13.40)).build();
            Preferences prefs = preferences();

            // when
            mapper.applyTo(domain, entity, location, prefs);

            // then
            then(entity.getUserId()).isEqualTo("user-9");
            then(entity.getName()).isEqualTo("Bob");
            then(entity.getCity()).isEqualTo("Berlin");
            then(entity.getLocation()).isSameAs(location);
            then(entity.getPreferences()).isSameAs(prefs);
            then(entity.isActive()).isTrue();
            then(entity.isPremium()).isFalse();
            then(entity.getHobbies()).containsExactly(com.tinder.contracts.dto.Hobby.COOKING);
        }
    }
}
