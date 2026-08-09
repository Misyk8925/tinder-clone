package com.tinder.profiles.domain.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

@DisplayName("Profile aggregate")
class ProfileTest {

    // Vienna (the profile's current position in most tests)
    private static final GeoPoint VIENNA = new GeoPoint(48.2092, 16.3728);
    // Berlin (~527 km from Vienna)
    private static final GeoPoint BERLIN = new GeoPoint(52.5200, 13.4050);

    private static Profile.Builder activeProfile() {
        return Profile.builder()
                .id(UUID.randomUUID())
                .userId("user-1")
                .name("Alice")
                .age(30)
                .gender("FEMALE")
                .city("Vienna")
                .active(true);
    }

    @Nested
    @DisplayName("when soft-deleted")
    class Deletion {

        @Test
        @DisplayName("is marked deleted, deactivated and timestamped")
        void marksDeleted() {
            // given
            Profile profile = activeProfile().build();

            // when
            profile.markAsDeleted();

            // then
            then(profile.isDeleted()).isTrue();
            then(profile.isActive()).isFalse();
            then(profile.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("is no longer deletable")
        void isNoLongerDeletable() {
            // given
            Profile profile = activeProfile().deleted(true).build();

            // when / then
            then(profile.isDeletable()).isFalse();
        }

        @Test
        @DisplayName("a live profile is deletable")
        void liveProfileIsDeletable() {
            then(activeProfile().build().isDeletable()).isTrue();
        }
    }

    @Nested
    @DisplayName("when (de)activating")
    class Activation {

        @Test
        @DisplayName("a deleted profile cannot be reactivated")
        void deletedProfileCannotBeReactivated() {
            // given
            Profile profile = activeProfile().deleted(true).active(false).build();

            // when
            profile.activate();

            // then
            then(profile.isActive()).isFalse();
        }

        @Test
        @DisplayName("a live profile can be reactivated")
        void liveProfileCanBeReactivated() {
            // given
            Profile profile = activeProfile().active(false).build();

            // when
            profile.activate();

            // then
            then(profile.isActive()).isTrue();
        }

        @Test
        @DisplayName("deactivating clears the active flag")
        void deactivatingClearsActive() {
            // given
            Profile profile = activeProfile().build();

            // when
            profile.deactivate();

            // then
            then(profile.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("when changing premium status")
    class Premium {

        @Test
        @DisplayName("activating stores the expiry")
        void activatingStoresExpiry() {
            // given
            Profile profile = activeProfile().build();
            LocalDateTime expiry = LocalDateTime.now().plusDays(30);

            // when
            profile.changePremium(true, expiry);

            // then
            then(profile.isPremium()).isTrue();
            then(profile.getPremiumExpiresAt()).isEqualTo(expiry);
        }

        @Test
        @DisplayName("revoking clears the expiry")
        void revokingClearsExpiry() {
            // given
            Profile profile = activeProfile().premium(true).premiumExpiresAt(LocalDateTime.now()).build();

            // when
            profile.changePremium(false, LocalDateTime.now().plusDays(30));

            // then
            then(profile.isPremium()).isFalse();
            then(profile.getPremiumExpiresAt()).isNull();
        }
    }

    @Nested
    @DisplayName("when evaluating movement")
    class Movement {

        @Test
        @DisplayName("accepts the first coordinate update when no position is known")
        void acceptsFirstUpdate() {
            // given
            Profile profile = activeProfile().build(); // no position

            // when / then
            then(profile.currentPosition()).isEmpty();
            then(profile.hasMovedBeyond(BERLIN, 1.0)).isTrue();
        }

        @Test
        @DisplayName("detects a move beyond the threshold")
        void detectsMoveBeyondThreshold() {
            // given
            Profile profile = activeProfile().position(VIENNA).build();

            // when / then
            then(profile.hasMovedBeyond(BERLIN, 1.0)).isTrue();
        }

        @Test
        @DisplayName("ignores GPS jitter below the threshold")
        void ignoresJitter() {
            // given
            Profile profile = activeProfile().position(VIENNA).build();
            GeoPoint elevenMetresNorth = new GeoPoint(VIENNA.latitude() + 0.0001, VIENNA.longitude()); // 0.0001° lat ≈ 11 m

            // when / then
            then(profile.hasMovedBeyond(elevenMetresNorth, 1.0)).isFalse();
        }

        @Test
        @DisplayName("treats a move at the threshold as significant")
        void treatsThresholdAsSignificant() {
            // given
            Profile profile = activeProfile().position(VIENNA).build();
            GeoPoint aboutAKmNorth = new GeoPoint(VIENNA.latitude() + 0.01, VIENNA.longitude()); // ~1.11 km

            // when / then
            then(profile.hasMovedBeyond(aboutAKmNorth, 1.0)).isTrue();
        }
    }

    @Nested
    @DisplayName("when relocating")
    class Relocation {

        @Test
        @DisplayName("updates position and city")
        void updatesPositionAndCity() {
            // given
            Profile profile = activeProfile().position(VIENNA).build();

            // when
            profile.relocate(BERLIN, "Berlin");

            // then
            then(profile.getPosition()).isEqualTo(BERLIN);
            then(profile.getCity()).isEqualTo("Berlin");
        }

        @Test
        @DisplayName("keeps the existing city when the new city is blank")
        void keepsCityWhenBlank() {
            // given
            Profile profile = activeProfile().position(VIENNA).build();

            // when
            profile.relocate(BERLIN, "   ");

            // then
            then(profile.getPosition()).isEqualTo(BERLIN);
            then(profile.getCity()).isEqualTo("Vienna");
        }
    }

    @Nested
    @DisplayName("the hobbies collection")
    class Hobbies {

        @Test
        @DisplayName("is copied defensively on replace")
        void copiedDefensivelyOnReplace() {
            // given
            Profile profile = activeProfile().build();
            List<Hobby> source = new ArrayList<>(List.of(Hobby.HIKING, Hobby.CYCLING));

            // when
            profile.replaceHobbies(source);
            source.clear(); // mutating the source must not affect the aggregate

            // then
            then(profile.getHobbies()).containsExactly(Hobby.HIKING, Hobby.CYCLING);
        }

        @Test
        @DisplayName("is exposed as an unmodifiable view")
        void exposedAsUnmodifiable() {
            // given
            Profile profile = activeProfile().build();

            // when / then
            thenThrownBy(() -> profile.getHobbies().add(Hobby.GYM))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
