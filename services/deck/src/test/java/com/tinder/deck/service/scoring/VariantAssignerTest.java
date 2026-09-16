package com.tinder.deck.service.scoring;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for VariantAssigner.
 *
 * <p>The whole attribution design rests on this being a pure, reproducible function of the
 * viewer id and the salt, so these tests pin determinism, salt sensitivity, split
 * proportions and the fallbacks.
 */
class VariantAssignerTest {

    private static final Map<String, Integer> EVEN_SPLIT = Map.of("baseline", 50, "reciprocal", 50);

    private final VariantAssigner assigner = new VariantAssigner();

    @Test
    void returnsTheSameVariantForTheSameViewerAndSalt() {
        // Given: one viewer
        UUID viewer = UUID.randomUUID();

        // When: assigning repeatedly
        String first = assigner.assign(viewer, "exp-1", EVEN_SPLIT, "baseline");
        String second = assigner.assign(viewer, "exp-1", EVEN_SPLIT, "baseline");

        // Then: the assignment is reproducible, which is what makes it storage-free
        assertThat(first).isEqualTo(second).isIn("baseline", "reciprocal");
    }

    @Test
    void doesNotDependOnSplitIterationOrder() {
        // Given: the same split declared in two different orders
        UUID viewer = UUID.randomUUID();
        Map<String, Integer> ordered = new LinkedHashMap<>();
        ordered.put("reciprocal", 50);
        ordered.put("baseline", 50);

        // When / Then: keys are sorted before bucketing, so configuration order is irrelevant
        assertThat(assigner.assign(viewer, "exp-1", ordered, "baseline"))
                .isEqualTo(assigner.assign(viewer, "exp-1", EVEN_SPLIT, "baseline"));
    }

    @Test
    void splitsTrafficRoughlyInTheConfiguredProportions() {
        // Given: ten thousand viewers on an even split
        int reciprocal = 0;
        for (int i = 0; i < 10_000; i++) {
            if ("reciprocal".equals(assigner.assign(UUID.randomUUID(), "exp-1", EVEN_SPLIT, "baseline"))) {
                reciprocal++;
            }
        }

        // Then: close enough to half that a real experiment is not skewed by the hash
        assertThat(reciprocal).isBetween(4_500, 5_500);
    }

    @Test
    void honoursAnUnevenSplit() {
        // Given: a 90/10 split
        Map<String, Integer> split = Map.of("baseline", 90, "reciprocal", 10);
        int reciprocal = 0;
        for (int i = 0; i < 10_000; i++) {
            if ("reciprocal".equals(assigner.assign(UUID.randomUUID(), "exp-1", split, "baseline"))) {
                reciprocal++;
            }
        }

        // Then: the small arm stays small
        assertThat(reciprocal).isBetween(700, 1_300);
    }

    @Test
    void ignoresVariantsWithZeroWeight() {
        // Given: a split that has been wound down to a single arm
        Map<String, Integer> split = Map.of("baseline", 100, "reciprocal", 0);

        // When / Then: the zero-weight arm is never assigned
        for (int i = 0; i < 500; i++) {
            assertThat(assigner.assign(UUID.randomUUID(), "exp-1", split, "baseline")).isEqualTo("baseline");
        }
    }

    @Test
    void fallsBackWhenTheSplitIsEmptyOrWeightless() {
        UUID viewer = UUID.randomUUID();

        assertThat(assigner.assign(viewer, "exp-1", Map.of(), "baseline")).isEqualTo("baseline");
        assertThat(assigner.assign(viewer, "exp-1", Map.of("reciprocal", 0), "baseline")).isEqualTo("baseline");
        assertThat(assigner.assign(null, "exp-1", EVEN_SPLIT, "baseline")).isEqualTo("baseline");
    }

    @Test
    void changingTheSaltReshufflesAssignments() {
        // Given: the same viewers under two salts
        int moved = 0;
        for (int i = 0; i < 500; i++) {
            UUID viewer = UUID.randomUUID();
            if (!assigner.assign(viewer, "exp-1", EVEN_SPLIT, "baseline")
                    .equals(assigner.assign(viewer, "exp-2", EVEN_SPLIT, "baseline"))) {
                moved++;
            }
        }

        // Then: a salt change moves a large share of viewers — which is exactly why
        // FR-9 forbids changing it on a running experiment
        assertThat(moved).isGreaterThan(100);
    }
}
