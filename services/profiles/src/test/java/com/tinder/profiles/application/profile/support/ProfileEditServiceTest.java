package com.tinder.profiles.application.profile.support;

import com.tinder.profiles.application.profile.command.CreateProfileCommand;
import com.tinder.profiles.application.profile.command.PatchProfileCommand;
import com.tinder.profiles.application.profile.command.ProfileEditCommand;
import com.tinder.profiles.application.profile.command.UpdateProfileCommand;
import com.tinder.profiles.application.profile.model.PreferencesData;
import com.tinder.profiles.application.profile.model.ProfileEdit;
import com.tinder.profiles.application.profile.exception.ProfileValidationException;
import com.tinder.profiles.application.profile.port.out.TextSanitizerPort;
import com.tinder.profiles.domain.profile.GeoPoint;
import com.tinder.profiles.domain.profile.Hobby;
import com.tinder.profiles.domain.profile.MatchingPreferences;
import com.tinder.profiles.domain.profile.Profile;
import com.tinder.profiles.domain.profile.ProfileChangeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenNoException;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

@DisplayName("ProfileEditService")
class ProfileEditServiceTest {

    private final ProfileEditService editService = new ProfileEditService(input -> input);

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
            thenThrownBy(() -> editService.requireLocationProvided(edit))
                    .isInstanceOf(ProfileValidationException.class)
                    .hasMessageContaining("Either city or GPS coordinates");
        }

        @Test
        @DisplayName("accepts an edit with a city")
        void acceptsEditWithCity() {
            // given
            ProfileEdit edit = new ProfileEdit(null, null, null, null, "Vienna", null, null, null);

            // when / then
            thenNoException().isThrownBy(() -> editService.requireLocationProvided(edit));
        }

        @Test
        @DisplayName("accepts an edit with coordinates")
        void acceptsEditWithCoordinates() {
            // given
            ProfileEdit edit = new ProfileEdit(null, null, null, null, null,
                    new GeoPoint(48.2, 16.3), null, null);

            // when / then
            thenNoException().isThrownBy(() -> editService.requireLocationProvided(edit));
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
            ProfileChangeSet changes = editService.detectChanges(existing, edit);

            // then
            then(changes.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("ignores fields that are absent from a partial edit")
        void ignoresAbsentFields() {
            // given a patch that only repeats the existing name
            ProfileEdit edit = new ProfileEdit("Alice", null, null, null, null, null, null, null);

            // when
            ProfileChangeSet changes = editService.detectChanges(existing, edit);

            // then
            then(changes.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("detects differing basic fields")
        void detectsBasicFieldChanges() {
            // given
            ProfileEdit edit = new ProfileEdit("Bob", 31, "MALE", "new bio", "Berlin", null, null, null);

            // when
            ProfileChangeSet changes = editService.detectChanges(existing, edit);

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
            then(editService.detectChanges(existing, changed).preferencesChanged()).isTrue();
            then(editService.detectChanges(existing, same).preferencesChanged()).isFalse();
        }

        @Test
        @DisplayName("treats provided hobbies as a change")
        void treatsProvidedHobbiesAsChange() {
            // given
            ProfileEdit edit = new ProfileEdit(null, null, null, null, null, null, null,
                    List.of(Hobby.HIKING));

            // when
            ProfileChangeSet changes = editService.detectChanges(existing, edit);

            // then
            then(changes.has("hobbies")).isTrue();
        }
    }

    @Nested
    @DisplayName("when translating a command into an edit")
    class Sanitization {

        /** Marks whatever it is given, so a missing call is visible in the result. */
        private static final TextSanitizerPort MARKING_SANITIZER =
                input -> input == null ? null : "clean:" + input;

        private final ProfileEditService sanitizingService = new ProfileEditService(MARKING_SANITIZER);

        /**
         * Every permitted {@link ProfileEditCommand}. Create, update and patch each
         * reach {@code toEdit}, so all three must route their text through the port.
         */
        static Stream<ProfileEditCommand> commands() {
            PreferencesData preferences = new PreferencesData(18, 40, "MALE", 50);
            return Stream.of(
                    new CreateProfileCommand("user-1", "Alice", 30, "FEMALE", "hi", "Vienna",
                            preferences, List.of("HIKING"), 48.2, 16.37),
                    new UpdateProfileCommand("user-1", "Alice", 30, "FEMALE", "hi", "Vienna",
                            preferences, List.of("HIKING"), 48.2, 16.37),
                    new PatchProfileCommand("user-1", "Alice", 30, "FEMALE", "hi", "Vienna",
                            preferences, List.of("HIKING"), 48.2, 16.37));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("commands")
        @DisplayName("routes every free-text field through the sanitizer")
        void sanitizesEveryFreeTextField(ProfileEditCommand command) {
            ProfileEdit edit = sanitizingService.toEdit(command);

            then(edit.name()).isEqualTo("clean:Alice");
            then(edit.gender()).isEqualTo("clean:FEMALE");
            then(edit.bio()).isEqualTo("clean:hi");
            then(edit.city()).isEqualTo("clean:Vienna");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("commands")
        @DisplayName("sanitizes the gender inside the preferences too")
        void sanitizesPreferencesGender(ProfileEditCommand command) {
            ProfileEdit edit = sanitizingService.toEdit(command);

            then(edit.preferences().gender()).isEqualTo("clean:MALE");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("commands")
        @DisplayName("leaves non-text fields untouched")
        void leavesNonTextFieldsUntouched(ProfileEditCommand command) {
            ProfileEdit edit = sanitizingService.toEdit(command);

            then(edit.age()).isEqualTo(30);
            then(edit.hobbies()).containsExactly(Hobby.HIKING);
            then(edit.position()).isNotNull();
            then(edit.preferences().minAge()).isEqualTo(18);
            then(edit.preferences().maxAge()).isEqualTo(40);
            then(edit.preferences().maxRange()).isEqualTo(50);
        }

        @Test
        @DisplayName("keeps a null field null rather than sanitizing it into a value")
        void keepsNullFieldsNull() {
            ProfileEditCommand patch = new PatchProfileCommand(
                    "user-1", null, null, null, null, "Vienna", null, null, null, null);

            ProfileEdit edit = sanitizingService.toEdit(patch);

            then(edit.name()).isNull();
            then(edit.gender()).isNull();
            then(edit.bio()).isNull();
            then(edit.city()).isEqualTo("clean:Vienna");
        }
    }
}
