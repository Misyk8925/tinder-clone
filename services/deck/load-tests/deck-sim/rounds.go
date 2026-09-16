package main

import (
	"math/rand"
	"sort"
)

// RoundResult is one cycle of "rank with what we know, watch what people do, learn from it".
type RoundResult struct {
	Round               int
	MatchesPer100Swipes float64
	// Covered is the share of profiles the counter has any evidence about. A ranker that
	// never shows a profile never learns anything about it.
	Covered float64
	// ExposureTopDecile is the share of all shown cards that went to the most-shown tenth
	// of profiles. Rising round on round is the feedback loop closing.
	ExposureTopDecile float64
}

// SimulateRounds runs the popularity ranker against itself over several cycles.
//
// A single pass can only show what a newcomer quota costs: reserved cards are cards not
// given to the best-ranked candidate, so matches fall. What it cannot show is what the
// quota buys, because that only appears once the ranker starts training on its own output.
// Measuring the cost alone would argue for a quota of zero, which is exactly the wrong
// conclusion.
func SimulateRounds(agents []Agent, config ScreenConfig, quota float64, rounds int) []RoundResult {
	index := newGeoIndex(agents)
	viewers := SelectViewers(agents, config.Viewers)
	popularity := make(Popularity, len(agents))
	exposure := make(map[string]int, len(agents))

	results := make([]RoundResult, 0, rounds)

	for round := 1; round <= rounds; round++ {
		random := rand.New(rand.NewSource(config.Seed + int64(round)))
		score := dampedPopularityScore(popularity)
		unseen := func(id string) bool { return popularity[id] == 0 }

		swipes := 0
		expectedMatches := 0.0

		for _, viewer := range viewers {
			pool := index.near(viewer, config.PoolSize, random)
			if len(pool) == 0 {
				continue
			}
			deck := RankTopTrained(score, unseen, viewer, pool, config.Window, quota, random)

			reach := viewer.Patience
			if reach > len(deck) {
				reach = len(deck)
			}
			for _, candidate := range deck[:reach] {
				exposure[candidate.ID]++
				expectedMatches += MutualProbability(viewer, candidate)
				if pairDraw(viewer.ID, candidate.ID, config.Seed+int64(round)) < LikeProbability(viewer, candidate) {
					popularity[candidate.ID]++
				}
			}
			swipes += reach
		}

		rate := 0.0
		if swipes > 0 {
			rate = expectedMatches / float64(swipes) * 100
		}
		results = append(results, RoundResult{
			Round:               round,
			MatchesPer100Swipes: rate,
			Covered:             float64(len(popularity)) / float64(len(agents)),
			ExposureTopDecile:   topDecileShare(exposure),
		})
	}
	return results
}

// topDecileShare is how much of all exposure the most-shown tenth of profiles received.
func topDecileShare(exposure map[string]int) float64 {
	if len(exposure) == 0 {
		return 0
	}
	counts := make([]int, 0, len(exposure))
	total := 0
	for _, count := range exposure {
		counts = append(counts, count)
		total += count
	}
	if total == 0 {
		return 0
	}
	sort.Ints(counts)

	cut := len(counts) - len(counts)/10
	top := 0
	for _, count := range counts[cut:] {
		top += count
	}
	return float64(top) / float64(total)
}
