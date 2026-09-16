package main

import (
	"math"
	"math/rand"
	"sort"
)

// DecayResult is one configuration of the counter's half-life after a run with arrivals.
type DecayResult struct {
	HalfLifeRounds      float64
	MatchesPer100Swipes float64
	// AppealRecall is the share of the genuinely most appealing tenth of profiles that the
	// counter also ranks in its top tenth. It is the number that separates a counter
	// tracking appeal from one tracking tenure.
	AppealRecall float64
	// NewcomerExposure is the share of all shown cards that went to profiles which arrived
	// after the first round.
	NewcomerExposure float64
}

// SimulateDecay runs the popularity ranker while new cohorts keep arriving.
//
// Arrivals are the whole point. Without them a counter that never decays is strictly better,
// because nothing it has learned ever goes stale — and that answer would be true of a world
// that stands still and false of this one. What decay protects against is incumbency: a
// veteran's count grows without bound while a newcomer starts at zero and cannot catch up,
// however much more appealing they are.
//
// halfLifeRounds of zero or less means no decay at all.
func SimulateDecay(agents []Agent, config ScreenConfig, quota float64, rounds int,
	arrivalRate float64, halfLifeRounds float64) DecayResult {
	return simulate(agents, config, quota, rounds, arrivalRate, halfLifeRounds, false)
}

// SimulateLikeRate is the same run with the counter replaced by a smoothed like rate, which
// is tenure-independent by construction and needs no decay to become one.
func SimulateLikeRate(agents []Agent, config ScreenConfig, quota float64, rounds int,
	arrivalRate float64) DecayResult {
	return simulate(agents, config, quota, rounds, arrivalRate, 0, true)
}

func simulate(agents []Agent, config ScreenConfig, quota float64, rounds int,
	arrivalRate float64, halfLifeRounds float64, useRate bool) DecayResult {

	arrivalRound := assignArrivals(agents, rounds, arrivalRate, config.Seed)
	index := newGeoIndex(agents)
	viewers := SelectViewers(agents, config.Viewers)

	popularity := make(Popularity, len(agents))
	rate := NewLikeRate(1, 8)
	exposure := make(map[string]int, len(agents))
	newcomerShown, totalShown := 0, 0

	factor := 1.0
	if halfLifeRounds > 0 {
		factor = math.Pow(0.5, 1/halfLifeRounds)
	}

	var lastRate float64
	for round := 1; round <= rounds; round++ {
		random := rand.New(rand.NewSource(config.Seed + int64(round)))
		score := dampedPopularityScore(popularity)
		unseen := func(id string) bool { return popularity[id] == 0 }
		if useRate {
			score = likeRateScore(rate)
			unseen = rate.Unseen
		}

		swipes := 0
		expectedMatches := 0.0

		for _, viewer := range viewers {
			if arrivalRound[viewer.ID] > round {
				continue
			}
			pool := present(index.near(viewer, config.PoolSize, random), arrivalRound, round)
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
				totalShown++
				if arrivalRound[candidate.ID] > 1 {
					newcomerShown++
				}
				expectedMatches += MutualProbability(viewer, candidate)
				liked := pairDraw(viewer.ID, candidate.ID, config.Seed+int64(round)) < LikeProbability(viewer, candidate)
				if liked {
					popularity[candidate.ID]++
				}
				rate.Observe(candidate.ID, liked)
			}
			swipes += reach
		}

		if swipes > 0 {
			lastRate = expectedMatches / float64(swipes) * 100
		}

		// Decay at the end of the round: the background job, never the read path.
		if factor < 1 {
			for id, value := range popularity {
				popularity[id] = value * factor
			}
		}
	}

	learned := popularity
	if useRate {
		learned = make(Popularity, len(agents))
		for _, agent := range agents {
			if !rate.Unseen(agent.ID) {
				learned[agent.ID] = rate.Of(agent.ID)
			}
		}
	}
	return DecayResult{
		HalfLifeRounds:      halfLifeRounds,
		MatchesPer100Swipes: lastRate,
		AppealRecall:        appealRecall(agents, learned),
		NewcomerExposure:    ratio(newcomerShown, totalShown),
	}
}

// assignArrivals spreads a share of the population across later rounds, so every round has
// profiles starting from nothing.
func assignArrivals(agents []Agent, rounds int, arrivalRate float64, seed int64) map[string]int {
	random := rand.New(rand.NewSource(seed + 555))
	arrival := make(map[string]int, len(agents))
	for _, agent := range agents {
		if arrivalRate > 0 && random.Float64() < arrivalRate {
			arrival[agent.ID] = 2 + random.Intn(maxInt(rounds-1, 1))
		} else {
			arrival[agent.ID] = 1
		}
	}
	return arrival
}

func present(pool []Agent, arrival map[string]int, round int) []Agent {
	filtered := pool[:0:0]
	for _, candidate := range pool {
		if arrival[candidate.ID] <= round {
			filtered = append(filtered, candidate)
		}
	}
	return filtered
}

// appealRecall asks whether the counter has learned appeal or merely tenure: of the truly
// most appealing tenth, how many does the counter also place in its top tenth?
func appealRecall(agents []Agent, popularity Popularity) float64 {
	if len(agents) < 10 {
		return 0
	}
	byAppeal := append([]Agent(nil), agents...)
	sort.Slice(byAppeal, func(a, b int) bool { return byAppeal[a].Appeal > byAppeal[b].Appeal })
	size := len(byAppeal) / 10

	trulyTop := make(map[string]bool, size)
	for _, agent := range byAppeal[:size] {
		trulyTop[agent.ID] = true
	}

	type counted struct {
		id    string
		value float64
	}
	counts := make([]counted, 0, len(agents))
	for _, agent := range agents {
		counts = append(counts, counted{agent.ID, popularity[agent.ID]})
	}
	sort.Slice(counts, func(a, b int) bool { return counts[a].value > counts[b].value })

	found := 0
	for _, entry := range counts[:size] {
		if trulyTop[entry.id] {
			found++
		}
	}
	return float64(found) / float64(size)
}

func ratio(part, whole int) float64 {
	if whole == 0 {
		return 0
	}
	return float64(part) / float64(whole)
}

func maxInt(a, b int) int {
	if a > b {
		return a
	}
	return b
}
