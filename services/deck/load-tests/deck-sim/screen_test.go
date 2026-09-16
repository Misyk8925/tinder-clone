package main

import (
	"math"
	"math/rand"
	"testing"
)

func screenConfig(viewers int) ScreenConfig {
	config := DefaultScreenConfig()
	config.Viewers = viewers
	config.PoolSize = 400
	config.Window = 100
	return config
}

// Given the symmetric control population, when every variant is screened, then baseline and
// reciprocal come out identical.
//
// This is the falsification check of FR-11. With identical window widths and radii the
// mirror terms carry the same information as the forward ones for everyone, so the ordering
// cannot change. A difference here would mean the screen is measuring something other than
// what it claims.
func TestControlPopulationLeavesReciprocalIndistinguishableFromBaseline(t *testing.T) {
	agents := generate(t, testParams(12_000, 0))

	results := Screen(agents, screenConfig(300))

	baseline := rateOf(results, VariantBaseline)
	reciprocal := rateOf(results, VariantReciprocal)
	if math.Abs(baseline-reciprocal) > 1e-9 {
		t.Fatalf("control population separated the arms: baseline %v, reciprocal %v", baseline, reciprocal)
	}
}

// Given an asymmetric population, when the variants are screened, then they order as
// random < baseline <= reciprocal <= oracle. A violation means the screen is broken, not
// that a ranking is clever: the oracle ranks by the very quantity being scored.
func TestVariantsOrderFromFloorToCeiling(t *testing.T) {
	agents := generate(t, testParams(12_000, 1))

	results := Screen(agents, screenConfig(300))

	random := rateOf(results, VariantRandom)
	baseline := rateOf(results, VariantBaseline)
	reciprocal := rateOf(results, VariantReciprocal)
	oracle := rateOf(results, VariantOracle)

	if !(random < baseline && baseline <= reciprocal+1e-9 && reciprocal <= oracle+1e-9) {
		t.Fatalf("variants out of order: random %v, baseline %v, reciprocal %v, oracle %v",
			random, baseline, reciprocal, oracle)
	}
}

// Given rising asymmetry, when the arms are screened, then reciprocal's advantage grows.
// Its whole claim is that it recovers information one-directional ranking throws away, and
// there is more of that information to recover as preferences disagree more.
func TestReciprocalAdvantageGrowsWithAsymmetry(t *testing.T) {
	previous := -1.0
	for _, asymmetry := range []float64{0, 0.5, 1} {
		agents := generate(t, testParams(12_000, asymmetry))
		results := Screen(agents, screenConfig(300))

		advantage := rateOf(results, VariantReciprocal) - rateOf(results, VariantBaseline)
		if advantage < previous-1e-9 {
			t.Fatalf("advantage fell from %v to %v at asymmetry %.2f", previous, advantage, asymmetry)
		}
		previous = advantage
	}
	if previous <= 0 {
		t.Fatal("reciprocal never gained anything; the screen cannot answer the question it exists for")
	}
}

// Given screened results, when headroom is computed, then it places each arm between the
// unranked floor and the achievable ceiling.
func TestHeadroomMeasuresTheShareOfTheReachableCeiling(t *testing.T) {
	results := []ScreenResult{
		{VariantRandom, 10, 100, 4.0},
		{VariantBaseline, 10, 100, 9.0},
		{VariantReciprocal, 10, 100, 14.0},
		{VariantLearned, 10, 100, 19.0},
		{VariantOracle, 10, 100, 24.0},
	}

	baseline := Headroom(results, VariantBaseline)
	reciprocal := Headroom(results, VariantReciprocal)

	// baseline covers 5 of the 20 points between floor and ceiling, reciprocal 10.
	if math.Abs(baseline-0.25) > 1e-9 || math.Abs(reciprocal-0.5) > 1e-9 {
		t.Fatalf("headroom is %v and %v, want 0.25 and 0.5", baseline, reciprocal)
	}
}

// Given a degenerate span, when headroom is computed, then it reports full coverage rather
// than dividing by zero. If the ceiling is no better than the floor there is nothing to win.
func TestHeadroomHandlesACeilingThatIsNoBetterThanTheFloor(t *testing.T) {
	results := []ScreenResult{
		{VariantRandom, 10, 100, 5.0},
		{VariantBaseline, 10, 100, 5.0},
		{VariantReciprocal, 10, 100, 5.0},
		{VariantLearned, 10, 100, 5.0},
		{VariantOracle, 10, 100, 5.0},
	}

	baseline := Headroom(results, VariantBaseline)
	reciprocal := Headroom(results, VariantReciprocal)

	if baseline != 1 || reciprocal != 1 {
		t.Fatalf("headroom is %v and %v, want full coverage on a zero span", baseline, reciprocal)
	}
}

// Given a neighbourhood larger than the pool limit, when a pool is drawn, then it is capped
// and excludes the viewer. The cap mirrors deck's own `search-limit`.
func TestPoolIsCappedAndExcludesTheViewer(t *testing.T) {
	agents := generate(t, testParams(20_000, 0.5))
	index := newGeoIndex(agents)
	random := rand.New(rand.NewSource(1))

	capped := 0
	for _, viewer := range agents[:200] {
		pool := index.near(viewer, 50, random)
		if len(pool) > 50 {
			t.Fatalf("pool of %d exceeds the limit", len(pool))
		}
		if len(pool) == 50 {
			capped++
		}
		for _, candidate := range pool {
			if candidate.ID == viewer.ID {
				t.Fatal("the viewer appeared in their own candidate pool")
			}
		}
	}
	if capped == 0 {
		t.Fatal("no pool ever reached the cap; the test is not exercising it")
	}
}
