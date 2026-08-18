package com.tinder.profiles.domain.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenNoException;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

@DisplayName("MatchingPreferences")
class MatchingPreferencesTest {

    @Nested
    @DisplayName("when constructed")
    class Construction {

        @Test
        @DisplayName("rejects a minimum age greater than the maximum age")
        void rejectsMinAgeGreaterThanMaxAge() {
             
            thenThrownBy(() -> new MatchingPreferences(40, 30, "FEMALE", 50))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("Minimum age cannot be greater than maximum age");
        }

        @Test
        @DisplayName("accepts an equal minimum and maximum age")
        void acceptsEqualMinAndMaxAge() {
             
            thenNoException().isThrownBy(() -> new MatchingPreferences(30, 30, "MALE", 50));
        }

        @Test
        @DisplayName("accepts a null age bound (no range to validate)")
        void acceptsNullAgeBound() {

            thenNoException().isThrownBy(() -> new MatchingPreferences(null, 30, "MALE", 50));
            thenNoException().isThrownBy(() -> new MatchingPreferences(18, null, "MALE", 50));
        }
    }

    @Nested
    @DisplayName("when comparing matching criteria")
    class SameCriteriaAs {

        @Test
        @DisplayName("is true for identical criteria")
        void trueForIdenticalCriteria() {
            // given
            MatchingPreferences a = new MatchingPreferences(18, 30, "FEMALE", 50);
            MatchingPreferences b = new MatchingPreferences(18, 30, "FEMALE", 50);

             
            then(a.sameCriteriaAs(b)).isTrue();
        }

        @Test
        @DisplayName("is false when any criterion differs")
        void falseWhenAnyCriterionDiffers() {
            // given
            MatchingPreferences base = new MatchingPreferences(18, 30, "FEMALE", 50);

             
            then(base.sameCriteriaAs(new MatchingPreferences(19, 30, "FEMALE", 50))).isFalse(); // minAge
            then(base.sameCriteriaAs(new MatchingPreferences(18, 31, "FEMALE", 50))).isFalse(); // maxAge
            then(base.sameCriteriaAs(new MatchingPreferences(18, 30, "MALE", 50))).isFalse();   // gender
            then(base.sameCriteriaAs(new MatchingPreferences(18, 30, "FEMALE", 75))).isFalse(); // maxRange
        }

        @Test
        @DisplayName("is false when compared against null")
        void falseWhenComparedAgainstNull() {
            // given
            MatchingPreferences a = new MatchingPreferences(18, 30, "FEMALE", 50);

             
            then(a.sameCriteriaAs(null)).isFalse();
        }

        @Test
        @DisplayName("treats matching null fields as equal")
        void treatsMatchingNullFieldsAsEqual() {
            // given
            MatchingPreferences a = new MatchingPreferences(null, null, null, 50);
            MatchingPreferences b = new MatchingPreferences(null, null, null, 50);

             
            then(a.sameCriteriaAs(b)).isTrue();
        }
    }
}
