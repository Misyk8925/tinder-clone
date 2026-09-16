package main

import "testing"

func decayConfig() ScreenConfig {
	config := DefaultScreenConfig()
	config.Viewers = 700
	config.PoolSize = 600
	config.Window = 200
	return config
}

// Given arriving cohorts, when a counter of raw likes is compared with a smoothed like rate,
// then the rate tracks true appeal far better.
//
// A raw count answers "how many people liked them", which a profile wins by being around
// longer. A rate answers "how often does a viewer who sees them like them", and tenure sits
// in its denominator instead of its numerator.
func TestLikeRateTracksAppealBetterThanACount(t *testing.T) {
	agents := generate(t, testParams(8_000, 0.5))
	config := decayConfig()

	counted := SimulateDecay(agents, config, 0.12, 6, 0.4, 0)
	rated := SimulateLikeRate(agents, config, 0.12, 6, 0.4)

	if rated.AppealRecall <= counted.AppealRecall {
		t.Fatalf("like rate recalled %.3f of the truly appealing, the count %.3f",
			rated.AppealRecall, counted.AppealRecall)
	}
	if rated.MatchesPer100Swipes <= counted.MatchesPer100Swipes {
		t.Fatalf("like rate produced %.3f matches per 100 swipes, the count %.3f",
			rated.MatchesPer100Swipes, counted.MatchesPer100Swipes)
	}
}

// Given a counter of raw likes, when it decays faster, then it tracks appeal better. That is
// the symptom that pointed at the rate: if decay merely discarded evidence, forgetting
// faster would make the estimate worse, not better.
func TestFasterDecayImprovesACountBecauseItRemovesTenure(t *testing.T) {
	agents := generate(t, testParams(8_000, 0.5))
	config := decayConfig()

	never := SimulateDecay(agents, config, 0.12, 6, 0.4, 0)
	fast := SimulateDecay(agents, config, 0.12, 6, 0.4, 1)

	if fast.AppealRecall <= never.AppealRecall {
		t.Fatalf("decay did not improve recall: %.3f with, %.3f without",
			fast.AppealRecall, never.AppealRecall)
	}
}

// Given a profile with very few impressions, when its rate is read, then the prior keeps a
// lucky streak from outranking everyone, and an unseen profile sits at the base rate rather
// than at zero.
func TestLikeRatePriorTamesSmallSamples(t *testing.T) {
	rate := NewLikeRate(1, 8)

	rate.Observe("lucky", true)
	rate.Observe("lucky", true)

	proven := "proven"
	for i := 0; i < 200; i++ {
		rate.Observe(proven, i%2 == 0)
	}

	if rate.Of("lucky") >= rate.Of(proven) {
		t.Fatalf("two lucky impressions outranked two hundred: %.3f vs %.3f",
			rate.Of("lucky"), rate.Of(proven))
	}
	if !rate.Unseen("nobody") {
		t.Fatal("a profile with no impressions was reported as seen")
	}
	if rate.Of("nobody") <= 0 {
		t.Fatal("an unseen profile scored zero and would never be ranked at all")
	}
}
