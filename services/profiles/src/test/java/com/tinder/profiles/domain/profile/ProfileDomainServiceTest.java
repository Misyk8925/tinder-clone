package com.tinder.profiles.domain.profile;

import com.tinder.contracts.dto.Hobby;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenNoException;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

@DisplayName("ProfileDomainService")
class ProfileDomainServiceTest {

    private final ProfileDomainService domainService = new ProfileDomainService();

    private Profile existing;

    @BeforeEach
    void setUp() {
        existing = Profile.builder()
                .id(UUID.randomUUID())
                .userId("user-1")
                .name("Alice")
                .age(30)
                .gender("FEMALE")
                .bio("hi")
                .city("Vienna")
                .preferences(new MatchingPreferences(18, 40, "MALE", 50))
                .build();
    }

    @Nested
    @DisplayName("when requiring a location")
    class RequireLocation {

        @Test
        @DisplayName("rejects an edit with neither city nor coordinates")
        void rejectsEditWithoutLocation() {
            // given
            ProfileEdit edit = new ProfileEdit("Alice", 30, "FEMALE", null, null, null, null, null);

            // when / then
            thenThrownBy(() -> domainService.requireLocationProvided(edit))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("Either city or GPS coordinates");
        }

        @Test
        @DisplayName("accepts an edit with a city")
        void acceptsEditWithCity() {
            // given
            ProfileEdit edit = new ProfileEdit(null, null, null, null, "Vienna", null, null, null);

            // when / then
            thenNoException().isThrownBy(() -> domainService.requireLocationProvided(edit));
        }

        @Test
        @DisplayName("accepts an edit with coordinates")
        void acceptsEditWithCoordinates() {
            // given
            ProfileEdit edit = new ProfileEdit(null, null, null, null, null,
                    new GeoPoint(48.2, 16.3), null, null);

            // when / then
            thenNoException().isThrownBy(() -> domainService.requireLocationProvided(edit));
        }
    }

    @Nested
    @DisplayName("when detecting changes")
    class DetectChanges {

        @Test
        @DisplayName("reports no changes when the edit matches the current state")
        void reportsNoChangesWhenIdentical() {
            // given
            ProfileEdit edit = new ProfileEdit("Alice", 30, "FEMALE", "hi", "Vienna", null, null, null);

            // when
            ProfileChangeSet changes = domainService.detectChanges(existing, edit);

            // then
            then(changes.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("ignores fields that are absent from a partial edit")
        void ignoresAbsentFields() {
            // given a patch that only repeats the existing name
            ProfileEdit edit = new ProfileEdit("Alice", null, null, null, null, null, null, null);

            // when
            ProfileChangeSet changes = domainService.detectChanges(existing, edit);

            // then
            then(changes.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("detects differing basic fields")
        void detectsBasicFieldChanges() {
            // given
            ProfileEdit edit = new ProfileEdit("Bob", 31, "MALE", "new bio", "Berlin", null, null, null);

            // when
            ProfileChangeSet changes = domainService.detectChanges(existing, edit);

            // then
            then(changes.changedFields()).containsExactlyInAnyOrder("name", "age", "gender", "bio", "city");
        }

        @Test
        @DisplayName("flags a preferences change only when the criteria differ")
        void flagsPreferencesOnlyWhenDifferent() {
            // given an edit whose preferences differ from the current ones (minAge 18 → 21)
            ProfileEdit changed = new ProfileEdit(null, null, null, null, null, null,
                    new MatchingPreferences(21, 40, "MALE", 50), null);
            // and an edit with identical preferences (matches setUp's 18/40/MALE/50)
            ProfileEdit same = new ProfileEdit(null, null, null, null, null, null,
                    new MatchingPreferences(18, 40, "MALE", 50), null);

            // when / then
            then(domainService.detectChanges(existing, changed).preferencesChanged()).isTrue();
            then(domainService.detectChanges(existing, same).preferencesChanged()).isFalse();
        }

        @Test
        @DisplayName("treats provided hobbies as a change")
        void treatsProvidedHobbiesAsChange() {
            // given
            ProfileEdit edit = new ProfileEdit(null, null, null, null, null, null, null,
                    List.of(Hobby.HIKING));

            // when
            ProfileChangeSet changes = domainService.detectChanges(existing, edit);

            // then
            then(changes.has("hobbies")).isTrue();
        }
    }
}
