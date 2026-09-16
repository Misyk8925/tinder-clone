package main

import (
	"math/rand"
	"testing"
)

func agent(age int, minAge, maxAge, radius int, latitude, longitude float64,
	taste []float64, appeal, selectivity float64) Agent {
	return Agent{
		ID: "a", Age: age, Latitude: latitude, Longitude: longitude,
		MinAge: minAge, MaxAge: maxAge, MaxRangeKm: radius,
		Taste: taste, Appeal: appeal, Selectivity: selectivity, Patience: 20,
	}
}

// Given any pair from a generated population, when a like probability is computed, then it
// is a probability.
func TestLikeProbabilityStaysWithinZeroAndOne(t *testing.T) {
	agents := generate(t, testParams(1_000, 1))
	random := rand.New(rand.NewSource(3))

	for i := 0; i < 20_000; i++ {
		p := LikeProbability(agents[random.Intn(len(agents))], agents[random.Intn(len(agents))])
		if p < 0 || p > 1 {
			t.Fatalf("like probability out of range: %v", p)
		}
	}
}

// Given a pair, when the mutual probability is computed from either side, then it is the
// same number — a match belongs to the pair, not to one viewer.
func TestMutualProbabilityIsSymmetric(t *testing.T) {
	agents := generate(t, testParams(500, 0.7))
	random := rand.New(rand.NewSource(5))

	for i := 0; i < 5_000; i++ {
		a := agents[random.Intn(len(agents))]
		b := agents[random.Intn(len(agents))]
		forward := MutualProbability(a, b)
		backward := MutualProbability(b, a)
		if forward != backward {
			t.Fatalf("mutual probability differs by direction: %v vs %v", forward, backward)
		}
	}
}

// Given two candidates identical in every feature the ranker can see — same age, same
// location — when they differ only in latent taste, then the simulated viewer separates
// them. This is FR-13 stated as a test: no sum of an age curve and a distance curve can
// reproduce this decision, so the contest between variants cannot be won by construction.
func TestLatentTasteSeparatesCandidatesTheRankerCannotDistinguish(t *testing.T) {
	viewer := agent(30, 25, 35, 100, 0, 0, []float64{1, 0}, 0.5, 0.3)
	aligned := agent(30, 25, 35, 100, 0, 0, []float64{1, 0}, 0.5, 0.3)
	opposed := agent(30, 25, 35, 100, 0, 0, []float64{-1, 0}, 0.5, 0.3)

	if LikeProbability(viewer, aligned) <= LikeProbability(viewer, opposed) {
		t.Fatal("taste has no effect; the latent model collapses into the ranker's feature space")
	}
}

// The same argument for appeal: identical age and distance, different popularity.
func TestAppealSeparatesOtherwiseIdenticalCandidates(t *testing.T) {
	viewer := agent(30, 25, 35, 100, 0, 0, []float64{1, 0}, 0.5, 0.3)
	popular := agent(30, 25, 35, 100, 0, 0, []float64{1, 0}, 0.9, 0.3)
	unpopular := agent(30, 25, 35, 100, 0, 0, []float64{1, 0}, 0.1, 0.3)

	if LikeProbability(viewer, popular) <= LikeProbability(viewer, unpopular) {
		t.Fatal("appeal has no effect; the popularity skew would never show up in outcomes")
	}
}

// Given two viewers differing only in selectivity, when both see the same candidate, then
// the pickier one likes less often.
func TestSelectivityLowersTheLikeRate(t *testing.T) {
	candidate := agent(30, 25, 35, 100, 0, 0, []float64{1, 0}, 0.6, 0.3)
	easygoing := agent(30, 25, 35, 100, 0, 0, []float64{1, 0}, 0.5, 0.1)
	picky := agent(30, 25, 35, 100, 0, 0, []float64{1, 0}, 0.5, 0.9)

	if LikeProbability(picky, candidate) >= LikeProbability(easygoing, candidate) {
		t.Fatal("selectivity does not reduce the like rate")
	}
}

// Given a candidate just outside the stated window, when the probability is computed, then
// it decays rather than dropping to zero: stated preferences bend behaviour, they do not
// gate it. A hard cut would make stated preferences perfectly predictive and hand the
// heuristics a result the real world does not give them.
func TestStatedWindowIsASoftPenaltyNotAFilter(t *testing.T) {
	viewer := agent(30, 25, 35, 100, 0, 0, []float64{1, 0}, 0.5, 0.1)
	inside := agent(34, 18, 99, 500, 0, 0, []float64{1, 0}, 0.8, 0.1)
	justOutside := agent(37, 18, 99, 500, 0, 0, []float64{1, 0}, 0.8, 0.1)
	farOutside := agent(60, 18, 99, 500, 0, 0, []float64{1, 0}, 0.8, 0.1)

	if LikeProbability(viewer, justOutside) <= 0 {
		t.Fatal("a candidate just outside the window was cut off entirely")
	}
	if LikeProbability(viewer, justOutside) >= LikeProbability(viewer, inside) {
		t.Fatal("being outside the window carries no penalty")
	}
	if LikeProbability(viewer, farOutside) >= LikeProbability(viewer, justOutside) {
		t.Fatal("the penalty does not grow with distance from the window")
	}
}

// Distance beyond the stated radius decays for the same reason.
func TestDistanceBeyondTheRadiusDecays(t *testing.T) {
	viewer := agent(30, 18, 99, 50, 0, 0, []float64{1, 0}, 0.5, 0.1)
	near := agent(30, 18, 99, 500, 0.1, 0, []float64{1, 0}, 0.8, 0.1)
	far := agent(30, 18, 99, 500, 3.0, 0, []float64{1, 0}, 0.8, 0.1)

	if LikeProbability(viewer, far) >= LikeProbability(viewer, near) {
		t.Fatal("distance beyond the radius carries no penalty")
	}
	if LikeProbability(viewer, far) <= 0 {
		t.Fatal("distance beyond the radius cut the candidate off entirely")
	}
}

// Given a generated population, when received interest is measured, then the most wanted
// tenth holds a large share of it. A flat population would hide both the reverse-index
// fan-out and the reason reciprocity matters: popular people are unreachable for most.
func TestInterestIsConcentrated(t *testing.T) {
	agents := generate(t, testParams(20_000, 0.5))

	share := InterestConcentration(agents, 1_500, 150, 21)
	if share < 0.30 {
		t.Fatalf("top decile receives %.4f of all like probability; the popularity skew is too flat", share)
	}
}
