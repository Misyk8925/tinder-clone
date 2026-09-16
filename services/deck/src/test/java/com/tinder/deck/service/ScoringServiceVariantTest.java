package com.tinder.deck.service;

import com.tinder.contracts.dto.SharedLocationDto;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.deck.config.ScoringProperties;
import com.tinder.deck.experiment.ActiveExperiment;
import com.tinder.deck.experiment.ActiveExperimentProvider;
import com.tinder.deck.service.scoring.AgeCompatibilityStrategy;
import com.tinder.deck.service.scoring.LocationProximityStrategy;
import com.tinder.deck.service.scoring.PopularityStrategy;
import com.tinder.deck.service.scoring.ReciprocalAgeStrategy;
import com.tinder.deck.service.scoring.ReciprocalProximityStrategy;
import com.tinder.deck.service.scoring.ScoringStrategy;
import com.tinder.deck.service.scoring.VariantAssigner;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the variant-aware ScoringService.
 *
 * <p>The load-bearing assertion is that {@code baseline} still sums exactly the two
 * strategies that existed before variants: without that, an A/B compares the new ranker
 * against something that is not the old one.
 */
class ScoringServiceVariantTest {

    private static final Map<String, ScoringStrategy> STRATEGY_BEANS = Map.of(
            "ageCompatibilityStrategy", new AgeCompatibilityStrategy(),
            "locationProximityStrategy", new LocationProximityStrategy(),
            "reciprocalAgeStrategy", new ReciprocalAgeStrategy(),
            "reciprocalProximityStrategy", new ReciprocalProximityStrategy(),
            "popularityStrategy", new PopularityStrategy());

    @Test
    void baselineSumsExactlyTheTwoOriginalStrategies() {
        // Given: the default catalogue and a mismatched pair
        ScoringService service = service(new ScoringProperties());
        SharedProfileDto viewer = profile(30, preferences(25, 35, 500), 0.0, 0.0);
        SharedProfileDto candidate = profile(40, preferences(18, 20, 5), 1.0, 0.0);

        // When: scoring under baseline
        double baseline = service.score(viewer, candidate, "baseline");

        // Then: it equals the hand-summed forward strategies, unchanged from before variants
        double expected = new AgeCompatibilityStrategy().calculateScore(viewer, candidate)
                + new LocationProximityStrategy().calculateScore(viewer, candidate);
        assertThat(baseline).isCloseTo(expected, within(1e-9));
    }

    @Test
    void reciprocalPenalisesACandidateWhoCouldNeverLikeTheViewerBack() {
        // Given: a candidate the viewer likes, who wants 18..20 and travels 5 km
        ScoringService service = service(new ScoringProperties());
        SharedProfileDto viewer = profile(30, preferences(25, 35, 500), 0.0, 0.0);
        SharedProfileDto unreachable = profile(28, preferences(18, 20, 5), 1.0, 0.0);
        SharedProfileDto mutual = profile(28, preferences(25, 35, 500), 1.0, 0.0);

        // When: both are scored under each variant
        double baselineGap = service.score(viewer, mutual, "baseline")
                - service.score(viewer, unreachable, "baseline");
        double reciprocalGap = service.score(viewer, mutual, "reciprocal")
                - service.score(viewer, unreachable, "reciprocal");

        // Then: baseline barely separates them; reciprocal ranks the mutual one clearly higher
        assertThat(baselineGap).isCloseTo(0.0, within(1e-9));
        assertThat(reciprocalGap).isGreaterThan(1.0);
    }

    @Test
    void assignsTheDefaultVariantWhileNoExperimentRuns() {
        // Given: an empty registry, which is the shipped state
        ScoringService service = service(new ScoringProperties());

        // When / Then: everyone scores as baseline
        assertThat(service.variantFor(profile(30, preferences(25, 35, 100), 0.0, 0.0)))
                .isEqualTo("baseline");
    }

    @Test
    void assignsBothVariantsOnceAnExperimentIsRunning() {
        // Given: a running 50/50 experiment in the registry
        ScoringService service = service(new ScoringProperties(), new ActiveExperiment(
                "reciprocity", "slice-2", "baseline",
                new LinkedHashMap<>(Map.of("baseline", 50, "reciprocal", 50))));

        // When: assigning many viewers
        long reciprocal = java.util.stream.IntStream.range(0, 1_000)
                .mapToObj(index -> profile(30, preferences(25, 35, 100), 0.0, 0.0))
                .map(service::variantFor)
                .filter("reciprocal"::equals)
                .count();

        // Then: both arms are populated
        assertThat(reciprocal).isBetween(400L, 600L);
    }

    @Test
    void scoresAnUnknownVariantAsTheDefaultInsteadOfFailing() {
        // Given: a variant name that is not in the catalogue (NFR-6)
        ScoringService service = service(new ScoringProperties());
        SharedProfileDto viewer = profile(30, preferences(25, 35, 100), 0.0, 0.0);
        SharedProfileDto candidate = profile(28, preferences(25, 35, 100), 0.0, 0.0);

        // When / Then: the deck build survives and scores as baseline
        assertThat(service.score(viewer, candidate, "no-such-variant"))
                .isCloseTo(service.score(viewer, candidate, "baseline"), within(1e-9));
    }

    @Test
    void refusesToStartWhenAVariantNamesAnUnknownStrategyBean() {
        // Given: a typo in the variant catalogue
        ScoringProperties properties = new ScoringProperties();
        properties.setVariants(new LinkedHashMap<>(Map.of("baseline", List.of("noSuchStrategy"))));

        // When / Then: it fails at startup rather than silently dropping a strategy
        assertThatThrownBy(() -> service(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("noSuchStrategy");
    }

    @Test
    void keepsScoringWhenTheRunningExperimentNamesAVariantThatNoLongerExists() {
        // Given: a stale experiment whose arms were removed from the catalogue (NFR-6)
        ScoringService service = service(new ScoringProperties(), new ActiveExperiment(
                "stale", "slice-2", "baseline", Map.of("removed-arm", 100)));
        SharedProfileDto viewer = profile(30, preferences(25, 35, 100), 0.0, 0.0);
        SharedProfileDto candidate = profile(28, preferences(25, 35, 100), 0.0, 0.0);

        // When / Then: the deck build survives and scores as baseline
        assertThat(service.score(viewer, candidate, service.variantFor(viewer)))
                .isCloseTo(service.score(viewer, candidate, "baseline"), within(1e-9));
    }

    private static ScoringService service(ScoringProperties properties) {
        return service(properties, (ActiveExperiment) null);
    }

    private static ScoringService service(ScoringProperties properties, ActiveExperiment active) {
        ActiveExperimentProvider provider = mock(ActiveExperimentProvider.class);
        when(provider.current()).thenReturn(Optional.ofNullable(active));
        return new ScoringService(STRATEGY_BEANS, properties, new VariantAssigner(), provider);
    }

    private static SharedProfileDto profile(Integer age, SharedPreferencesDto preferences,
                                            Double latitude, Double longitude) {
        SharedLocationDto location = new SharedLocationDto(UUID.randomUUID(), latitude, longitude,
                "Test City", LocalDateTime.now(), LocalDateTime.now());
        return new SharedProfileDto(UUID.randomUUID(), "Test", age, "Bio", "Test City", true,
                location, preferences, false, List.of(), List.of());
    }

    private static SharedPreferencesDto preferences(int minAge, int maxAge, int maxRange) {
        return new SharedPreferencesDto(minAge, maxAge, "ANY", maxRange);
    }
}
