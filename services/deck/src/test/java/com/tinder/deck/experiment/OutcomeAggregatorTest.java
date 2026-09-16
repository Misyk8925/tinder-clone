package com.tinder.deck.experiment;

import com.tinder.deck.service.scoring.VariantAssigner;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for OutcomeAggregator.
 *
 * <p>This is where the storage-free assignment closes the loop: nothing recorded which arm
 * a viewer was in, so the bucketing here has to reproduce it from the id and the salt. If
 * it did not, every result would be attributed to the wrong variant.
 */
class OutcomeAggregatorTest {

    private final VariantAssigner assigner = new VariantAssigner();
    private final OutcomeAggregator aggregator = new OutcomeAggregator(assigner);

    private static Experiment experiment(Map<String, Integer> split) {
        return new Experiment(UUID.randomUUID(), "reciprocity", "salt-1", "baseline",
                ExperimentStatus.RUNNING, split, Instant.now(), Instant.now(), null);
    }

    @Test
    void bucketsViewersIntoTheArmTheAssignerWouldHaveGivenThem() {
        // Given: an experiment and a set of viewers with identical activity
        Experiment running = experiment(Map.of("baseline", 50, "reciprocal", 50));
        List<ViewerOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            outcomes.add(new ViewerOutcome(UUID.randomUUID(), 20, 4, 1));
        }

        // When: aggregating
        List<VariantOutcome> results = aggregator.aggregate(running, outcomes);

        // Then: each viewer landed in the arm the assigner independently produces
        long expectedReciprocal = outcomes.stream()
                .filter(outcome -> "reciprocal".equals(
                        assigner.assign(outcome.viewerId(), "salt-1", running.split(), "baseline")))
                .count();
        VariantOutcome reciprocal = byName(results, "reciprocal");
        assertThat(reciprocal.viewers()).isEqualTo(expectedReciprocal);
        assertThat(results.stream().mapToLong(VariantOutcome::viewers).sum()).isEqualTo(600);
    }

    @Test
    void reportsAnArmWithNoActivityInsteadOfDroppingIt() {
        // Given: an experiment whose reciprocal arm produced nothing
        Experiment running = experiment(Map.of("baseline", 100, "reciprocal", 0));

        // When: aggregating an empty set
        List<VariantOutcome> results = aggregator.aggregate(running, List.of());

        // Then: both arms are present at zero — a missing row reads as a missing arm
        assertThat(results).extracting(VariantOutcome::variant).containsExactly("baseline", "reciprocal");
        assertThat(results).allSatisfy(outcome -> {
            assertThat(outcome.viewers()).isZero();
            assertThat(outcome.matchesPer100Swipes()).isZero();
        });
    }

    @Test
    void computesRatesPerHundredSwipes() {
        // Given: one arm with a known ratio
        Experiment running = experiment(Map.of("baseline", 100));
        List<ViewerOutcome> outcomes = List.of(
                new ViewerOutcome(UUID.randomUUID(), 100, 25, 5),
                new ViewerOutcome(UUID.randomUUID(), 100, 15, 3));

        // When / Then: 8 matches and 40 likes across 200 swipes
        VariantOutcome baseline = byName(aggregator.aggregate(running, outcomes), "baseline");
        assertThat(baseline.swipes()).isEqualTo(200);
        assertThat(baseline.matchesPer100Swipes()).isCloseTo(4.0, within(1e-9));
        assertThat(baseline.likesPer100Swipes()).isCloseTo(20.0, within(1e-9));
    }

    @Test
    void bracketsTheRateWithAnInterval() {
        // Given: an arm of forty viewers with a mixture of outcomes
        Experiment running = experiment(Map.of("baseline", 100));

        // When / Then: the interval surrounds the point estimate and stays a rate
        VariantOutcome baseline = byName(aggregator.aggregate(running, arm(40, 25, 5, 0, 1, 2)), "baseline");
        assertThat(baseline.matchesPer100SwipesLower()).isLessThanOrEqualTo(baseline.matchesPer100Swipes());
        assertThat(baseline.matchesPer100SwipesUpper()).isGreaterThanOrEqualTo(baseline.matchesPer100Swipes());
        assertThat(baseline.matchesPer100SwipesLower()).isGreaterThanOrEqualTo(0.0);
        assertThat(baseline.matchesPer100SwipesUpper()).isLessThanOrEqualTo(100.0);
    }

    @Test
    void widensTheIntervalWhenTheSameSwipesComeFromFewerPeople() {
        // Given: two arms with the same number of swipes and the same match rate, but one
        // spread over forty viewers and the other over four
        Experiment running = experiment(Map.of("baseline", 100));
        VariantOutcome many = byName(aggregator.aggregate(running, arm(40, 25, 5, 0, 1, 2, 1)), "baseline");
        VariantOutcome few = byName(aggregator.aggregate(running, arm(4, 250, 50, 0, 10, 20, 10)), "baseline");

        assertThat(many.swipes()).isEqualTo(few.swipes());

        // Then: four people are four observations, not a thousand. This is the defect a
        // swipe-level binomial interval had: it reported both of these as equally certain.
        double manyWidth = many.matchesPer100SwipesUpper() - many.matchesPer100SwipesLower();
        double fewWidth = few.matchesPer100SwipesUpper() - few.matchesPer100SwipesLower();
        assertThat(fewWidth).isGreaterThan(manyWidth);
    }

    @Test
    void narrowsTheIntervalAsViewersGrow() {
        // Given: the same per-viewer behaviour measured on ten times more people
        Experiment running = experiment(Map.of("baseline", 100));
        VariantOutcome small = byName(aggregator.aggregate(running, arm(30, 20, 4, 0, 1, 2)), "baseline");
        VariantOutcome large = byName(aggregator.aggregate(running, arm(300, 20, 4, 0, 1, 2)), "baseline");

        double smallWidth = small.matchesPer100SwipesUpper() - small.matchesPer100SwipesLower();
        double largeWidth = large.matchesPer100SwipesUpper() - large.matchesPer100SwipesLower();
        assertThat(largeWidth).isLessThan(smallWidth);
    }

    @Test
    void producesTheSameIntervalOnEveryCall() {
        // Given: the same arm summarised twice
        Experiment running = experiment(Map.of("baseline", 100));
        List<ViewerOutcome> outcomes = arm(50, 20, 4, 0, 1, 2);

        VariantOutcome first = byName(aggregator.aggregate(running, outcomes), "baseline");
        VariantOutcome second = byName(aggregator.aggregate(running, outcomes), "baseline");

        // Then: reloading the results page must not move the interval
        assertThat(first.matchesPer100SwipesLower()).isEqualTo(second.matchesPer100SwipesLower());
        assertThat(first.matchesPer100SwipesUpper()).isEqualTo(second.matchesPer100SwipesUpper());
    }

    @Test
    void refusesToClaimAnythingFromASingleViewer() {
        // Given: one viewer, however many swipes they made
        Experiment running = experiment(Map.of("baseline", 100));

        VariantOutcome baseline = byName(aggregator.aggregate(running,
                List.of(new ViewerOutcome(UUID.randomUUID(), 5_000, 1_000, 200))), "baseline");

        // Then: the full range. One person says nothing about how people differ, and
        // resampling them would otherwise report perfect certainty.
        assertThat(baseline.matchesPer100SwipesLower()).isZero();
        assertThat(baseline.matchesPer100SwipesUpper()).isEqualTo(100.0);
    }

    @Test
    void reportsNoUncertaintyWhenEveryViewerIsIdentical() {
        // Given: an arm whose viewers behave exactly alike
        Experiment running = experiment(Map.of("baseline", 100));

        VariantOutcome baseline = byName(aggregator.aggregate(running, arm(40, 25, 5, 1)), "baseline");

        // Then: zero width, and that is right rather than a bug. Resampling over people
        // measures how people differ; when they do not differ, the mean is not in doubt.
        // A binomial interval over swipes would have invented uncertainty here.
        assertThat(baseline.matchesPer100SwipesLower()).isEqualTo(baseline.matchesPer100SwipesUpper());
        assertThat(baseline.matchesPer100Swipes()).isCloseTo(4.0, within(1e-9));
    }

    @Test
    void keepsTheIntervalAtZeroWhenNothingMatched() {
        // Given: an arm where no match happened at all
        Experiment running = experiment(Map.of("baseline", 100));

        VariantOutcome baseline = byName(aggregator.aggregate(running, arm(30, 20, 4, 0L)), "baseline");

        // Then: every resample also has no matches, so there is nothing to bracket
        assertThat(baseline.matchesPer100SwipesLower()).isZero();
        assertThat(baseline.matchesPer100SwipesUpper()).isZero();
    }

    @Test
    void treatsAnArmWithNoSwipesAsZeroRatherThanDividingByZero() {
        Experiment running = experiment(Map.of("baseline", 100));

        VariantOutcome baseline = byName(aggregator.aggregate(running, arm(5, 0, 0, 0L)), "baseline");

        assertThat(baseline.matchesPer100Swipes()).isZero();
        assertThat(baseline.matchesPer100SwipesUpper()).isZero();
    }

    /**
     * An arm whose viewers differ from one another, cycling through {@code matchesCycle}.
     *
     * <p>Viewers must differ for these tests to mean anything: the interval is resampled
     * over people, so an arm of identical people has nothing to vary and correctly comes
     * back with zero width. See {@link #reportsNoUncertaintyWhenEveryViewerIsIdentical}.
     */
    private static List<ViewerOutcome> arm(int viewers, long swipes, long likes, long... matchesCycle) {
        List<ViewerOutcome> outcomes = new ArrayList<>(viewers);
        for (int i = 0; i < viewers; i++) {
            outcomes.add(new ViewerOutcome(
                    UUID.randomUUID(), swipes, likes, matchesCycle[i % matchesCycle.length]));
        }
        return outcomes;
    }

    private static VariantOutcome byName(List<VariantOutcome> results, String variant) {
        return results.stream().filter(outcome -> variant.equals(outcome.variant())).findFirst().orElseThrow();
    }
}
