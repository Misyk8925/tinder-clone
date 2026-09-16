package main

import (
	"math"
	"math/rand"
)

// LastCoverageSeen and LastCoveragePerProfile record how much evidence the most recent
// training run had. Package-level because they are diagnostics for the operator running a
// sweep, not part of any result a caller consumes.
var (
	LastCoverageSeen       int
	LastCoveragePerProfile float64
)

// ScreenResult is one variant's expected outcome for one population.
type ScreenResult struct {
	Variant             Variant
	Viewers             int
	Swipes              int
	MatchesPer100Swipes float64
}

// ScreenConfig controls the offline screen.
type ScreenConfig struct {
	Viewers       int     // how many agents are evaluated
	TrainViewers  int     // how many agents produce the history the learned arms train on
	Window        int     // cards materialised per deck; the platform's TOTAL_WINDOW
	PoolSize      int     // candidates the ranking sees, mirroring deck's search-limit
	NewcomerQuota float64 // share of each learned deck reserved for profiles with no evidence
	Seed          int64
}

func DefaultScreenConfig() ScreenConfig {
	// PoolSize mirrors `deck.search-limit: 2000` in the deck service. Ranking an entire
	// dense city per viewer would be both slower than anything the platform does and
	// unfaithful to it: the real builder never sees more than this many candidates.
	// TrainViewers is much larger than Viewers on purpose: a real platform has far more
	// history than one deck build's worth. Training on the evaluation set alone would
	// leave the factorisation with no evidence about almost every profile and would
	// measure the sample size, not the method.
	return ScreenConfig{
		Viewers: 2_000, TrainViewers: 20_000, Window: 500, PoolSize: 2_000,
		NewcomerQuota: 0.05, Seed: 1,
	}
}

// Screen estimates, without any running service, how many matches each ranking variant
// would produce per 100 swipes.
//
// It is a screening step, not the experiment. It answers one question — whether there is
// room between today's ranking and the best any ranking could do — because that question is
// a property of the population and the ordering formulas, not of a deployment. If there is
// no room, the paid run has nothing to find.
//
// A viewer's expected matches over the cards they reach is the sum of the true mutual-like
// probability of those cards. Ranking changes only *which* cards land in reach, which is
// exactly what this compares.
func Screen(agents []Agent, config ScreenConfig) []ScreenResult {
	index := newGeoIndex(agents)
	viewers := SelectViewers(agents, config.Viewers)

	// The learned arm needs a history to learn from, and in production that history is
	// whatever the *current* ranking chose to show. Training on a baseline pass reproduces
	// that: a candidate baseline never surfaced has no observed popularity, however wanted
	// they would have been. That exposure bias is part of what is being measured, not a
	// flaw in the measurement.
	history, popularity := observedHistory(agents, index, config)
	model := TrainEmbeddings(history, DefaultTrainingConfig())
	LastCoverageSeen, LastCoveragePerProfile = model.Coverage(history, len(agents))
	damped := dampedPopularityScore(popularity)
	embedded := embeddingScore(model)
	unseen := func(id string) bool { return !model.Seen(id) }

	results := make([]ScreenResult, 0, len(AllVariants()))
	for _, variant := range AllVariants() {
		// Same seed per variant so the random arm is not compared against a different
		// shuffle than the one the other arms were measured beside.
		variantRandom := rand.New(rand.NewSource(config.Seed))

		swipes := 0
		expectedMatches := 0.0
		for _, viewer := range viewers {
			pool := index.near(viewer, config.PoolSize, variantRandom)
			if len(pool) == 0 {
				continue
			}
			var deck []Agent
			switch variant {
			case VariantLearned:
				deck = RankTopTrained(damped, unseen, viewer, pool, config.Window,
					config.NewcomerQuota, variantRandom)
			case VariantEmbedding:
				deck = RankTopTrained(embedded, unseen, viewer, pool, config.Window,
					config.NewcomerQuota, variantRandom)
			default:
				deck = RankTop(variant, viewer, pool, config.Window, variantRandom)
			}

			reach := viewer.Patience
			if reach > len(deck) {
				reach = len(deck)
			}
			for _, candidate := range deck[:reach] {
				expectedMatches += MutualProbability(viewer, candidate)
			}
			swipes += reach
		}

		rate := 0.0
		if swipes > 0 {
			rate = expectedMatches / float64(swipes) * 100
		}
		results = append(results, ScreenResult{variant, len(viewers), swipes, rate})
	}
	return results
}

// observedHistory replays what today's ranking would have shown and what people would have
// done about it. It is the only training input the learned arms get.
//
// Decisions are drawn, not averaged: a factorisation trains on individual yes/no events, and
// handing it expectations would give it information no platform records. Popularity is a
// count of those same drawn likes.
//
// Trained on a *baseline* pass, because that is what a real training set is: a profile
// today's ranking never surfaced carries no evidence, however wanted they would have been.
// The exposure bias is part of the result rather than an error in it.
func observedHistory(agents []Agent, index *geoIndex, config ScreenConfig) ([]SwipeRecord, Popularity) {
	trainers := SelectViewers(agents, config.TrainViewers)
	popularity := make(Popularity, len(agents))
	history := make([]SwipeRecord, 0, len(trainers)*16)
	random := rand.New(rand.NewSource(config.Seed + 101))

	for _, viewer := range trainers {
		pool := index.near(viewer, config.PoolSize, random)
		if len(pool) == 0 {
			continue
		}
		deck := RankTop(VariantBaseline, viewer, pool, config.Window, random)

		reach := viewer.Patience
		if reach > len(deck) {
			reach = len(deck)
		}
		for _, candidate := range deck[:reach] {
			liked := pairDraw(viewer.ID, candidate.ID, config.Seed) < LikeProbability(viewer, candidate)
			history = append(history, SwipeRecord{viewer.ID, candidate.ID, liked})
			if liked {
				popularity[candidate.ID]++
			}
		}
	}
	return history, popularity
}

// Headroom is the share of the achievable ceiling that a variant already reaches, with the
// unranked floor as zero. NFR-10 cancels the paid run when baseline reaches 95%.
func Headroom(results []ScreenResult, variant Variant) float64 {
	floor := rateOf(results, VariantRandom)
	ceiling := rateOf(results, VariantOracle)
	span := ceiling - floor
	if span <= 0 {
		return 1
	}
	return (rateOf(results, variant) - floor) / span
}

func rateOf(results []ScreenResult, variant Variant) float64 {
	for _, result := range results {
		if result.Variant == variant {
			return result.MatchesPer100Swipes
		}
	}
	return 0
}

// geoIndex buckets agents into one-degree cells so a viewer's candidate pool is the people
// actually near them. Scoring every agent against every viewer would be faithful and far
// too slow; a random pool would be fast and would flatter the distance term, because most
// of it would be people nobody could reach.
type geoIndex struct {
	cells map[[2]int][]Agent
}

func newGeoIndex(agents []Agent) *geoIndex {
	cells := make(map[[2]int][]Agent)
	for _, agent := range agents {
		key := cellOf(agent)
		cells[key] = append(cells[key], agent)
	}
	return &geoIndex{cells: cells}
}

func (index *geoIndex) near(viewer Agent, limit int, random *rand.Rand) []Agent {
	centre := cellOf(viewer)
	pool := make([]Agent, 0, limit)
	seen := 0

	for latitude := centre[0] - 1; latitude <= centre[0]+1; latitude++ {
		for longitude := centre[1] - 1; longitude <= centre[1]+1; longitude++ {
			for _, candidate := range index.cells[[2]int{latitude, longitude}] {
				if candidate.ID == viewer.ID {
					continue
				}
				// Reservoir sampling: an unbiased `limit`-sized sample of the neighbourhood
				// without materialising all of it. Taking the first `limit` instead would
				// bias the pool towards whichever cell happened to be iterated first.
				seen++
				if len(pool) < limit {
					pool = append(pool, candidate)
				} else if index := random.Intn(seen); index < limit {
					pool[index] = candidate
				}
			}
		}
	}
	return pool
}

func cellOf(agent Agent) [2]int {
	return [2]int{int(math.Floor(agent.Latitude)), int(math.Floor(agent.Longitude))}
}
