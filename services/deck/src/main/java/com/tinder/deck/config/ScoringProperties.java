package com.tinder.deck.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The catalogue of named scoring variants.
 *
 * <p>It stays in configuration because it names Spring beans, not data. Which variants a
 * viewer is split across, and with what salt, belongs to the persisted experiment
 * registry instead — see {@code com.tinder.deck.experiment}.
 */
@Data
@ConfigurationProperties(prefix = "deck.scoring")
public class ScoringProperties {

    /** Variant used when no experiment is running, or when an assigned variant is unknown. */
    private String defaultVariant = "baseline";

    /**
     * Variant name to the {@code ScoringStrategy} bean names it sums. Defaults live here
     * rather than only in {@code application.yml} so an environment that has not been
     * updated still resolves {@code baseline} to today's exact behaviour.
     */
    private Map<String, List<String>> variants = defaultVariants();

    private static Map<String, List<String>> defaultVariants() {
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        defaults.put("baseline", List.of(
                "ageCompatibilityStrategy",
                "locationProximityStrategy"));
        defaults.put("popularity", List.of("popularityStrategy"));
        defaults.put("reciprocal", List.of(
                "ageCompatibilityStrategy",
                "locationProximityStrategy",
                "reciprocalAgeStrategy",
                "reciprocalProximityStrategy"));
        return defaults;
    }

}
