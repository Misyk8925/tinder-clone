package com.tinder.profiles.domain.profile;

import com.tinder.contracts.event.v1.ChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

@DisplayName("ProfileChangeSet")
class ProfileChangeSetTest {

    @Nested
    @DisplayName("given no changes")
    class Empty {

        @Test
        @DisplayName("reports empty and classifies as NON_CRITICAL")
        void emptyClassifiesAsNonCritical() {
            // given
            ProfileChangeSet changes = ProfileChangeSet.empty();

            // when / then
            then(changes.isEmpty()).isTrue();
            then(changes.classify()).isEqualTo(ChangeType.NON_CRITICAL);
        }
    }

    @Nested
    @DisplayName("when classifying by priority")
    class Classification {

        @Test
        @DisplayName("a city change wins over every other change")
        void cityChangeWins() {
            // given a set where three priority tiers changed at once
            ProfileChangeSet changes = ProfileChangeSet.empty()
                    .add("age")              // CRITICAL_FIELDS tier
                    .markPreferencesChanged()// PREFERENCES tier
                    .add("city");            // LOCATION_CHANGE tier — highest

            // when / then — the highest tier wins
            then(changes.classify()).isEqualTo(ChangeType.LOCATION_CHANGE);
        }

        @Test
        @DisplayName("a preferences change wins over critical fields when no city changed")
        void preferencesWinsOverCritical() {
            // given
            ProfileChangeSet changes = ProfileChangeSet.empty()
                    .add("age")
                    .markPreferencesChanged();

            // when / then
            then(changes.classify()).isEqualTo(ChangeType.PREFERENCES);
        }

        @Test
        @DisplayName("age or gender alone is CRITICAL_FIELDS")
        void ageOrGenderIsCritical() {
            then(ProfileChangeSet.empty().add("age").classify()).isEqualTo(ChangeType.CRITICAL_FIELDS);
            then(ProfileChangeSet.empty().add("gender").classify()).isEqualTo(ChangeType.CRITICAL_FIELDS);
        }

        @Test
        @DisplayName("name or bio alone is NON_CRITICAL")
        void nameOrBioIsNonCritical() {
            then(ProfileChangeSet.empty().add("name").classify()).isEqualTo(ChangeType.NON_CRITICAL);
            then(ProfileChangeSet.empty().add("bio").classify()).isEqualTo(ChangeType.NON_CRITICAL);
        }
    }

    @Nested
    @DisplayName("when preferences are marked changed")
    class PreferencesChanged {

        @Test
        @DisplayName("flags the change, exposes the field and is no longer empty")
        void flagsPreferenceChange() {
            // given
            ProfileChangeSet changes = ProfileChangeSet.empty();

            // when
            changes.markPreferencesChanged();

            // then
            then(changes.preferencesChanged()).isTrue();
            then(changes.has("preferences")).isTrue();
            then(changes.isEmpty()).isFalse();
        }
    }

    @Nested
    @DisplayName("the exposed changed fields")
    class ChangedFieldsView {

        @Test
        @DisplayName("are an unmodifiable view")
        void areUnmodifiable() {
            // given
            ProfileChangeSet changes = ProfileChangeSet.empty().add("name");

            // when / then
            thenThrownBy(() -> changes.changedFields().add("hacked"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
