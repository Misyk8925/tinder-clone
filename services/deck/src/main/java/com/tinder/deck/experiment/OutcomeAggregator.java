package com.tinder.deck.experiment;

import com.tinder.deck.service.scoring.VariantAssigner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Buckets per-viewer outcomes into experiment arms and puts an interval on each.
 *
 * <p>Nothing was stored when a viewer was assigned: the variant is recomputed here from the
 * viewer id and the experiment's salt, which is the whole reason no contract had to carry
 * it. That also means a changed salt would silently re-bucket history, and why the registry
 * refuses to change one on a running experiment.
 */
@Component
public class OutcomeAggregator {

    /** Resamples per arm. A thousand is enough for a 95% interval and cheap at bench sizes. */
    private static final int BOOTSTRAP_SAMPLES = 1_000;

    private final VariantAssigner variantAssigner;

    public OutcomeAggregator(VariantAssigner variantAssigner) {
        this.variantAssigner = variantAssigner;
    }

    public List<VariantOutcome> aggregate(Experiment experiment, List<ViewerOutcome> outcomes) {
        Map<String, List<ViewerOutcome>> byVariant = new LinkedHashMap<>();
        experiment.split().keySet().stream().sorted()
                .forEach(variant -> byVariant.put(variant, new ArrayList<>()));

        for (ViewerOutcome outcome : outcomes) {
            String variant = variantAssigner.assign(
                    outcome.viewerId(), experiment.salt(), experiment.split(), experiment.defaultVariant());
            byVariant.computeIfAbsent(variant, ignored -> new ArrayList<>()).add(outcome);
        }

        List<VariantOutcome> results = new ArrayList<>(byVariant.size());
        byVariant.forEach((variant, arm) -> results.add(summarise(experiment, variant, arm)));
        results.sort(Comparator.comparing(VariantOutcome::variant));
        return results;
    }

    private VariantOutcome summarise(Experiment experiment, String variant, List<ViewerOutcome> arm) {
        long swipes = arm.stream().mapToLong(ViewerOutcome::swipes).sum();
        long likes = arm.stream().mapToLong(ViewerOutcome::likes).sum();
        long matches = arm.stream().mapToLong(ViewerOutcome::matches).sum();

        double[] interval = bootstrapInterval(arm, seedFor(experiment.salt(), variant));

        return new VariantOutcome(variant, arm.size(), swipes, likes, matches,
                per100(likes, swipes), per100(matches, swipes), interval[0], interval[1]);
    }

    /**
     * A 95% interval for matches per 100 swipes, resampled over *viewers*.
     *
     * <p>The obvious alternative — a binomial interval over swipes — treats every swipe as
     * an independent trial. Swipes by one person are not independent: a picky viewer likes
     * rarely across all of them, an appealing one matches often across all of them. Using
     * swipes as the sample size therefore claims far more evidence than exists and reports
     * a difference as proven before it is.
     *
     * <p>Resampling whole viewers with replacement fixes the unit of observation and, as a
     * side effect, settles how to weight a viewer with three swipes against one with fifty:
     * their swipes travel with them.
     *
     * <p>Seeded from the salt and the variant, so reloading the results page does not
     * produce a slightly different interval each time.
     */
    private static double[] bootstrapInterval(List<ViewerOutcome> arm, long seed) {
        if (arm.isEmpty()) {
            return new double[]{0, 0};
        }
        if (arm.size() < 2) {
            // One viewer says nothing about how viewers differ, and resampling would
            // return that same person every time — a zero-width interval claiming
            // certainty from a single person. Report the full range instead.
            return new double[]{0, 100};
        }

        Random random = new Random(seed);
        double[] rates = new double[BOOTSTRAP_SAMPLES];

        for (int sample = 0; sample < BOOTSTRAP_SAMPLES; sample++) {
            long swipes = 0;
            long matches = 0;
            for (int draw = 0; draw < arm.size(); draw++) {
                ViewerOutcome picked = arm.get(random.nextInt(arm.size()));
                swipes += picked.swipes();
                matches += picked.matches();
            }
            rates[sample] = per100(matches, swipes);
        }

        Arrays.sort(rates);
        return new double[]{percentile(rates, 0.025), percentile(rates, 0.975)};
    }

    private static double percentile(double[] sorted, double fraction) {
        int index = (int) Math.round(fraction * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static double per100(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator * 100;
    }

    private static long seedFor(String salt, String variant) {
        return (long) (salt + ":" + variant).hashCode() * 0x9e3779b97f4a7c15L;
    }
}
