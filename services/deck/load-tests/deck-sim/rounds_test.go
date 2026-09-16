package main

import "testing"

// Given a quota, when decks are built over several rounds, then more profiles acquire
// evidence than with no quota at all. This is what the quota buys, and it is invisible in a
// single pass.
func TestQuotaBreaksTheExposureLoop(t *testing.T) {
	agents := generate(t, testParams(6_000, 0.5))
	config := DefaultScreenConfig()
	config.Viewers = 800
	config.PoolSize = 600
	config.Window = 200

	without := SimulateRounds(agents, config, 0, 4)
	with := SimulateRounds(agents, config, 0.12, 4)

	if with[len(with)-1].Covered <= without[len(without)-1].Covered {
		t.Fatalf("quota did not widen coverage: %v with, %v without",
			with[len(with)-1].Covered, without[len(without)-1].Covered)
	}
}

// Given a quota finer than one slot per card the viewer reaches, when decks are built, then
// it changes nothing. Reserved slots the viewer never gets to are not reserved slots — the
// first implementation appended them past the window and the quota was inert at every size.
func TestQuotaFinerThanTheViewerReachIsInert(t *testing.T) {
	agents := generate(t, testParams(6_000, 0.5))
	config := DefaultScreenConfig()
	config.Viewers = 400
	config.PoolSize = 600
	config.Window = 200

	none := SimulateRounds(agents, config, 0, 2)
	// Mean patience is about 25, so one slot every 500 cards is far beyond any viewer.
	tiny := SimulateRounds(agents, config, 0.002, 2)

	if tiny[len(tiny)-1].Covered != none[len(none)-1].Covered {
		t.Fatalf("a quota below the viewers' reach changed coverage: %v vs %v",
			tiny[len(tiny)-1].Covered, none[len(none)-1].Covered)
	}
}

// Given rounds of the ranker training on its own output, when coverage is tracked, then it
// grows: each round's exposure becomes the next round's evidence.
func TestCoverageGrowsAcrossRounds(t *testing.T) {
	agents := generate(t, testParams(6_000, 0.5))
	config := DefaultScreenConfig()
	config.Viewers = 800
	config.PoolSize = 600
	config.Window = 200

	results := SimulateRounds(agents, config, 0.12, 4)

	for i := 1; i < len(results); i++ {
		if results[i].Covered < results[i-1].Covered {
			t.Fatalf("coverage fell from %v to %v at round %d",
				results[i-1].Covered, results[i].Covered, results[i].Round)
		}
	}
	if results[len(results)-1].Covered <= results[0].Covered {
		t.Fatal("coverage never grew; the rounds are not feeding the counter")
	}
}
