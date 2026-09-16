package main

import (
	"math"
	"math/rand"
	"testing"
)

// Pins the curves this file shares with the Java strategies in
// services/deck/src/main/java/com/tinder/deck/service/scoring.
//
// Honest limitation: this cannot detect a change on the Java side. Nothing here executes
// that code. It anchors the numbers so a reader can compare the two by eye, and it fails
// loudly if the Go copy drifts — which is half the risk, not all of it.
func TestRankingParityWithTheJavaStrategies(t *testing.T) {
	if ageStrategyWeight != 1.0 || proximityStrategyWeight != 0.8 {
		t.Fatalf("weights are %v and %v; AgeCompatibilityStrategy.getWeight() is 1.0 and "+
			"LocationProximityStrategy.getWeight() is 0.8", ageStrategyWeight, proximityStrategyWeight)
	}

	// AgeCompatibilityStrategy.ageProximityFactor
	for _, c := range []struct {
		age, minAge, maxAge int
		want                float64
	}{
		{30, 25, 35, 1.0}, // inside the window
		{25, 25, 35, 1.0}, // inclusive lower edge
		{35, 25, 35, 1.0}, // inclusive upper edge
		{40, 25, 35, 0.5}, // five years past, half the weight
		{20, 25, 35, 0.5}, // five years short
		{45, 25, 35, 0.0}, // ten years past, the decay floor
		{60, 25, 35, 0.0}, // never negative
	} {
		if got := ageFit(c.age, c.minAge, c.maxAge); math.Abs(got-c.want) > 1e-9 {
			t.Fatalf("ageFit(%d, %d..%d) = %v, want %v", c.age, c.minAge, c.maxAge, got, c.want)
		}
	}

	// LocationProximityStrategy: linear to the radius, zero beyond it.
	for _, c := range []struct {
		distance, radius, want float64
	}{
		{0, 100, 1.0},
		{50, 100, 0.5},
		{100, 100, 0.0},
		{150, 100, 0.0},
	} {
		if got := proximityFit(c.distance, c.radius); math.Abs(got-c.want) > 1e-9 {
			t.Fatalf("proximityFit(%v, %v) = %v, want %v", c.distance, c.radius, got, c.want)
		}
	}
}

// Given two candidates identical to the viewer in age and distance but wanting completely
// different people, when both are scored, then baseline cannot tell them apart and
// reciprocal can. This is the defect the whole feature exists for, stated as a test.
func TestBaselineIgnoresWhetherTheCandidateCouldWantTheViewer(t *testing.T) {
	viewer := agent(30, 18, 99, 500, 0, 0, []float64{1, 0}, 0.5, 0.3)
	mutual := agent(30, 25, 35, 500, 0.1, 0, []float64{1, 0}, 0.5, 0.3)
	unreachable := agent(30, 18, 20, 1, 0.1, 0, []float64{1, 0}, 0.5, 0.3)

	if math.Abs(baselineScore(viewer, mutual)-baselineScore(viewer, unreachable)) > 1e-9 {
		t.Fatal("baseline separated them; it must read only the viewer's own preferences")
	}
	if reciprocalScore(viewer, mutual) <= reciprocalScore(viewer, unreachable) {
		t.Fatal("reciprocal did not rank the reachable candidate higher")
	}
}

// Given a pool, when it is ranked, then the window bounds the result and the viewer is
// never in their own deck.
func TestRankTopReturnsAtMostTheWindow(t *testing.T) {
	agents := generate(t, testParams(400, 0.5))
	viewer := agents[0]
	random := rand.New(rand.NewSource(1))

	for _, variant := range AllVariants() {
		deck := RankTop(variant, viewer, agents[1:], 50, random)
		if len(deck) != 50 {
			t.Fatalf("%s returned %d cards, want the 50-card window", variant, len(deck))
		}
		for _, card := range deck {
			if card.ID == viewer.ID {
				t.Fatalf("%s put the viewer in their own deck", variant)
			}
		}
	}
}

// Given the oracle variant, when a deck is ranked, then it is ordered by the true mutual
// probability — it is the ceiling the other variants are measured against.
func TestOracleOrdersByMutualProbability(t *testing.T) {
	agents := generate(t, testParams(800, 0.5))
	viewer := agents[0]

	deck := RankTop(VariantOracle, viewer, agents[1:], 30, rand.New(rand.NewSource(1)))

	for i := 1; i < len(deck); i++ {
		before := MutualProbability(viewer, deck[i-1])
		after := MutualProbability(viewer, deck[i])
		if after > before+1e-12 {
			t.Fatalf("oracle put %v before %v", before, after)
		}
	}
}
