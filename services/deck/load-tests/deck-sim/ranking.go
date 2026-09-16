package main

import (
	"math"
	"math/rand"
	"sort"
)

// The ranking variants, reimplemented here to score a deck offline.
//
// These mirror the Java strategies in services/deck/.../service/scoring: the same age
// curve, the same linear distance decay, the same weights of 1.0 and 0.8. That duplication
// is the price of screening without a running platform, and it is a real hazard: if either
// side changes, this screen silently measures a ranking the service no longer uses.
// TestRankingParityWithTheJavaStrategies pins the shared constants and curves, but it
// cannot detect a change on the Java side — nothing here executes that code.
const (
	ageStrategyWeight       = 1.0
	proximityStrategyWeight = 0.8
	ageDecayYears           = 10.0
	unknownPreferenceScore  = 0.5
	defaultRadiusKm         = 100.0
)

// Variant is a way of ordering candidates for one viewer.
type Variant string

const (
	// VariantBaseline is today's production ranking: the viewer's own age and distance
	// preferences, and nothing about whether the candidate could want them back.
	VariantBaseline Variant = "baseline"
	// VariantReciprocal adds the mirror of both terms.
	VariantReciprocal Variant = "reciprocal"
	// VariantRandom is the floor: no ranking at all.
	VariantRandom Variant = "random"
	// VariantLearned orders by what a ranker could learn from swipe history: how often a
	// candidate is liked by anyone, times how well the two fit each other's stated
	// preferences. It reads no latent state — popularity comes from observed behaviour.
	VariantLearned Variant = "learned"
	// VariantEmbedding is the same idea taken further: a factorisation of the observed
	// swipe matrix, so taste is learned rather than approximated by popularity alone.
	VariantEmbedding Variant = "embedding"
	// VariantOracle is the ceiling: order by the true probability of a mutual like. It
	// reads latent state, so it exists only inside the simulation.
	VariantOracle Variant = "oracle"
)

func AllVariants() []Variant {
	return []Variant{VariantRandom, VariantBaseline, VariantReciprocal, VariantLearned, VariantEmbedding, VariantOracle}
}

// Popularity is how often each profile was liked, as the platform would have recorded it.
type Popularity map[string]float64

// learnedScore is the cheapest thing a ranker could learn that the heuristics cannot know:
// that some profiles are wanted far more than others.
//
// A match needs both sides, so the quantity to maximise is P(viewer likes candidate) times
// P(candidate likes viewer). Popularity stands in for the part of each that belongs to the
// person being judged, and the stated-preference fits carry the part that belongs to the
// pair. The viewer's own popularity is constant across their deck, so it cannot change the
// order and is left out.
func learnedScore(popularity Popularity) func(Agent, Agent) float64 {
	return func(viewer, candidate Agent) float64 {
		distance := DistanceKm(viewer, candidate)
		forward := ageFit(candidate.Age, viewer.MinAge, viewer.MaxAge) *
			proximityFit(distance, float64(viewer.MaxRangeKm))
		backward := ageFit(viewer.Age, candidate.MinAge, candidate.MaxAge) *
			proximityFit(distance, float64(candidate.MaxRangeKm))
		return popularity[candidate.ID] * forward * backward
	}
}

// ageFit is AgeCompatibilityStrategy.ageProximityFactor: 1.0 inside the window, decaying
// linearly to zero over ten years outside it.
func ageFit(age, minAge, maxAge int) float64 {
	switch {
	case age >= minAge && age <= maxAge:
		return 1.0
	case age < minAge:
		return math.Max(0, 1.0-float64(minAge-age)/ageDecayYears)
	default:
		return math.Max(0, 1.0-float64(age-maxAge)/ageDecayYears)
	}
}

// proximityFit is LocationProximityStrategy: linear decay to the radius, zero beyond it.
func proximityFit(distance, radius float64) float64 {
	if radius <= 0 {
		radius = defaultRadiusKm
	}
	if distance > radius {
		return 0
	}
	return 1.0 - distance/radius
}

func baselineScore(viewer, candidate Agent) float64 {
	distance := DistanceKm(viewer, candidate)
	return ageFit(candidate.Age, viewer.MinAge, viewer.MaxAge)*ageStrategyWeight +
		proximityFit(distance, float64(viewer.MaxRangeKm))*proximityStrategyWeight
}

func reciprocalScore(viewer, candidate Agent) float64 {
	distance := DistanceKm(viewer, candidate)
	return baselineScore(viewer, candidate) +
		ageFit(viewer.Age, candidate.MinAge, candidate.MaxAge)*ageStrategyWeight +
		proximityFit(distance, float64(candidate.MaxRangeKm))*proximityStrategyWeight
}

// RankTop orders the pool for one viewer under one variant and returns the first `window`
// candidates — the part of a deck a viewer can ever reach.
func RankTop(variant Variant, viewer Agent, pool []Agent, window int, random *rand.Rand) []Agent {
	return rankTopWith(scorer(variant), variant, viewer, pool, window, random)
}

// RankTopTrained is RankTop for the variants that need something learned first, with the
// newcomer quota applied: a share of every deck is reserved for profiles the model has no
// evidence about, because otherwise a new profile is never shown, never swiped on, and
// never acquires the evidence that would let it be shown.
//
// The reserved slots are **interleaved through the deck, not appended to it**. Appending
// them looked reasonable — the quota is exposure, not promotion — and did nothing at all: a
// viewer with patience for twenty-five cards never reaches position 480 of a 500-card
// window. A quota beyond the viewer's reach is not a quota.
func RankTopTrained(score func(Agent, Agent) float64, unseen func(string) bool,
	viewer Agent, pool []Agent, window int, quota float64, random *rand.Rand) []Agent {

	ranked := rankTopWith(score, VariantLearned, viewer, pool, window, random)
	if quota <= 0 || unseen == nil {
		return ranked
	}

	inDeck := make(map[string]bool, len(ranked))
	for _, candidate := range ranked {
		inDeck[candidate.ID] = true
	}

	reserved := int(math.Round(float64(window) * quota))
	newcomers := make([]Agent, 0, reserved)
	for _, candidate := range pool {
		if len(newcomers) == reserved {
			break
		}
		if candidate.ID != viewer.ID && !inDeck[candidate.ID] && unseen(candidate.ID) {
			newcomers = append(newcomers, candidate)
		}
	}
	if len(newcomers) == 0 {
		return ranked
	}

	// One reserved slot every 1/quota cards, so the share holds wherever the viewer stops.
	every := int(math.Round(1 / quota))
	if every < 1 {
		every = 1
	}

	merged := make([]Agent, 0, len(ranked))
	next := 0
	for position := 0; len(merged) < len(ranked); position++ {
		if next < len(newcomers) && position%every == every-1 {
			merged = append(merged, newcomers[next])
			next++
			continue
		}
		if len(ranked) == 0 {
			break
		}
		merged = append(merged, ranked[0])
		ranked = ranked[1:]
	}
	return merged
}

func rankTopWith(score func(Agent, Agent) float64, variant Variant, viewer Agent,
	pool []Agent, window int, random *rand.Rand) []Agent {
	ranked := make([]Agent, len(pool))
	copy(ranked, pool)

	switch variant {
	case VariantRandom:
		random.Shuffle(len(ranked), func(a, b int) { ranked[a], ranked[b] = ranked[b], ranked[a] })
	default:
		scores := make(map[string]float64, len(ranked))
		for _, candidate := range ranked {
			scores[candidate.ID] = score(viewer, candidate)
		}
		sort.SliceStable(ranked, func(a, b int) bool {
			return scores[ranked[a].ID] > scores[ranked[b].ID]
		})
	}

	if window < len(ranked) {
		ranked = ranked[:window]
	}
	return ranked
}

func scorer(variant Variant) func(Agent, Agent) float64 {
	switch variant {
	case VariantReciprocal:
		return reciprocalScore
	case VariantOracle:
		return MutualProbability
	default:
		return baselineScore
	}
}
