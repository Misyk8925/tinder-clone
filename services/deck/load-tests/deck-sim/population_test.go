package main

import (
	"math"
	"math/rand"
	"testing"
)

func testParams(size int, asymmetry float64) Params {
	params := DefaultParams()
	params.Size = size
	params.Asymmetry = asymmetry
	return params
}

func generate(t *testing.T, params Params) []Agent {
	t.Helper()
	agents, err := Generate(params)
	if err != nil {
		t.Fatalf("generate: %v", err)
	}
	return agents
}

// Given the same seed and params, when the population is generated twice, then the agents
// are identical — a sweep has to be re-runnable and comparable.
func TestGenerateIsReproducible(t *testing.T) {
	first := generate(t, testParams(500, 0.5))
	second := generate(t, testParams(500, 0.5))

	for i := range first {
		if first[i].ID != second[i].ID || first[i].Age != second[i].Age ||
			first[i].MinAge != second[i].MinAge || first[i].MaxRangeKm != second[i].MaxRangeKm {
			t.Fatalf("agent %d differs between runs: %+v vs %+v", i, first[i], second[i])
		}
	}
}

// Given asymmetry 0, when stated preferences are compared in both directions, then
// acceptance is exactly symmetric. This is the control population of FR-11: it is what
// gives the series a way to fail.
func TestControlPopulationIsSymmetric(t *testing.T) {
	agents := generate(t, testParams(2_000, 0))
	random := rand.New(rand.NewSource(7))

	for i := 0; i < 20_000; i++ {
		a := agents[random.Intn(len(agents))]
		b := agents[random.Intn(len(agents))]
		if a.ID == b.ID {
			continue
		}
		if AcceptsStated(a, b) != AcceptsStated(b, a) {
			t.Fatalf("asymmetric pair in the control population:\n a=%+v\n b=%+v", a, b)
		}
	}
}

// Given a rising asymmetry parameter, when one-sided acceptance is measured, then it rises
// with it. Without this the parameter would not be the axis NFR-7 requires.
func TestOneSidedAcceptanceRisesWithAsymmetry(t *testing.T) {
	previous := -1.0
	for _, asymmetry := range []float64{0, 0.25, 0.5, 0.75, 1} {
		agents := generate(t, testParams(4_000, asymmetry))
		oneSided, _ := AcceptanceRates(agents, 60_000, 11)

		if asymmetry == 0 && oneSided != 0 {
			t.Fatalf("control population reported %.4f one-sided pairs, want 0", oneSided)
		}
		if oneSided < previous {
			t.Fatalf("one-sided share fell from %.4f to %.4f at asymmetry %.2f", previous, oneSided, asymmetry)
		}
		previous = oneSided
	}
	if previous <= 0 {
		t.Fatal("one-sided acceptance never became positive; the asymmetry axis does nothing")
	}
}

// Given a generated population, when profiles are inspected, then every stated preference
// is within the bounds the platform's own contract allows.
func TestStatedPreferencesStayWithinContractBounds(t *testing.T) {
	agents := generate(t, testParams(5_000, 1))

	for _, agent := range agents {
		if agent.MinAge < 18 || agent.MaxAge > 99 || agent.MinAge > agent.MaxAge {
			t.Fatalf("age window outside SharedPreferencesDto bounds: %+v", agent)
		}
		if agent.MaxRangeKm < 1 {
			t.Fatalf("radius below the contract minimum: %+v", agent)
		}
		if agent.Age < 18 {
			t.Fatalf("agent below the platform minimum age: %+v", agent)
		}
	}
}

// Given a generated population, when locations are inspected, then they cluster rather
// than spread evenly, so geographic density is uneven.
func TestPopulationClustersGeographically(t *testing.T) {
	agents := generate(t, testParams(5_000, 0.5))

	buckets := map[[2]int]int{}
	for _, agent := range agents {
		buckets[[2]int{int(math.Floor(agent.Latitude)), int(math.Floor(agent.Longitude))}]++
	}

	largest := 0
	for _, count := range buckets {
		if count > largest {
			largest = count
		}
	}
	if float64(largest)/float64(len(agents)) < 0.05 {
		t.Fatalf("largest cell holds only %.4f of the population; geography is not lumpy",
			float64(largest)/float64(len(agents)))
	}
}

func TestGenerateRejectsInvalidParams(t *testing.T) {
	for name, params := range map[string]Params{
		"zero size":           testParams(0, 0.5),
		"negative asymmetry":  testParams(10, -0.1),
		"asymmetry above one": testParams(10, 1.5),
	} {
		if _, err := Generate(params); err == nil {
			t.Fatalf("%s was accepted", name)
		}
	}
}
