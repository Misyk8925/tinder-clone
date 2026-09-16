package main

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"sort"
	"strings"
	"sync"
	"testing"
	"time"
)

type recordedSwipe struct {
	Profile1ID string `json:"profile1Id"`
	Profile2ID string `json:"profile2Id"`
	Decision   bool   `json:"decision"`
	IsSuper    bool   `json:"isSuper"`
}

// fakeDeck stands in for gateway plus deck-read plus swipes.
type fakeDeck struct {
	mutex        sync.Mutex
	pages        [][]string // successive pages of profile ids
	swipes       []recordedSwipe
	authHeaders  []string
	buildingLeft int
	deckCalls    int
}

func (f *fakeDeck) server(t *testing.T) *httptest.Server {
	t.Helper()
	mux := http.NewServeMux()

	mux.HandleFunc("/api/v2/deck", func(writer http.ResponseWriter, request *http.Request) {
		f.mutex.Lock()
		defer f.mutex.Unlock()
		f.authHeaders = append(f.authHeaders, request.Header.Get("Authorization"))

		if f.buildingLeft > 0 {
			f.buildingLeft--
			writer.Header().Set("Retry-After", "2")
			writer.WriteHeader(http.StatusAccepted)
			_, _ = writer.Write([]byte(`{"state":"BUILDING","retryAfterSeconds":2}`))
			return
		}

		index := f.deckCalls
		f.deckCalls++
		if index >= len(f.pages) {
			_ = json.NewEncoder(writer).Encode(DeckPage{Items: nil, State: "EMPTY", Generation: 7})
			return
		}

		items := make([]DeckCard, 0, len(f.pages[index]))
		for _, id := range f.pages[index] {
			items = append(items, DeckCard{ProfileID: id})
		}
		page := DeckPage{Items: items, State: "READY", Generation: 7}
		if index+1 < len(f.pages) {
			next := "cursor-" + string(rune('a'+index))
			page.NextCursor = &next
		}
		_ = json.NewEncoder(writer).Encode(page)
	})

	mux.HandleFunc("/api/v1/swipes", func(writer http.ResponseWriter, request *http.Request) {
		body, _ := io.ReadAll(request.Body)
		var swipe recordedSwipe
		_ = json.Unmarshal(body, &swipe)

		f.mutex.Lock()
		f.swipes = append(f.swipes, swipe)
		f.authHeaders = append(f.authHeaders, request.Header.Get("Authorization"))
		f.mutex.Unlock()

		writer.WriteHeader(http.StatusAccepted)
	})

	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)
	return server
}

func driverFor(t *testing.T, server *httptest.Server, agents []Agent) *Driver {
	t.Helper()
	config := DriverConfig{
		BaseURL: server.URL, PageLimit: 20, Concurrency: 1,
		BuildRetry: time.Millisecond, BuildWaits: 5, Seed: 1,
	}
	return NewDriver(config, MintedTokens{issuer(t)}, agents)
}

// Given a viewer with patience 3 and a deck of ten cards, when it browses, then it stops at
// three. Patience is what makes ranking order reach the outcome at all.
func TestViewerStopsAtItsPatience(t *testing.T) {
	agents := generate(t, testParams(40, 0.5))
	viewer := agents[0]
	viewer.Patience = 3

	ids := make([]string, 0, 10)
	for _, candidate := range agents[1:11] {
		ids = append(ids, candidate.ID)
	}
	fake := &fakeDeck{pages: [][]string{ids}}
	driver := driverFor(t, fake.server(t), agents)

	outcomes, err := driver.Run([]Agent{viewer})
	if err != nil {
		t.Fatalf("run: %v", err)
	}

	if outcomes[0].Shown != 3 {
		t.Fatalf("viewer saw %d cards, want 3", outcomes[0].Shown)
	}
	if len(fake.swipes) != 3 {
		t.Fatalf("%d swipes posted, want one per shown card", len(fake.swipes))
	}
}

// Given a browsing viewer, when it swipes, then every request carries its bearer token and
// every swipe names the viewer as profile1.
func TestEveryRequestCarriesTheViewerToken(t *testing.T) {
	agents := generate(t, testParams(20, 0.5))
	viewer := agents[0]
	viewer.Patience = 2
	fake := &fakeDeck{pages: [][]string{{agents[1].ID, agents[2].ID}}}
	driver := driverFor(t, fake.server(t), agents)

	if _, err := driver.Run([]Agent{viewer}); err != nil {
		t.Fatalf("run: %v", err)
	}

	for _, header := range fake.authHeaders {
		if !strings.HasPrefix(header, "Bearer ey") {
			t.Fatalf("request carried %q instead of a bearer token", header)
		}
	}
	for _, swipe := range fake.swipes {
		if swipe.Profile1ID != viewer.ID {
			t.Fatalf("swipe came from %s, want the viewer %s", swipe.Profile1ID, viewer.ID)
		}
		if swipe.IsSuper {
			t.Fatal("the simulator posted a super like, which it never decides")
		}
	}
}

// Given a deck still materialising, when the driver meets 202 BUILDING, then it retries
// rather than treating the viewer as having an empty deck.
func TestBuildingDeckIsRetried(t *testing.T) {
	agents := generate(t, testParams(20, 0.5))
	viewer := agents[0]
	viewer.Patience = 2
	fake := &fakeDeck{pages: [][]string{{agents[1].ID, agents[2].ID}}, buildingLeft: 2}
	driver := driverFor(t, fake.server(t), agents)

	outcomes, err := driver.Run([]Agent{viewer})
	if err != nil {
		t.Fatalf("run: %v", err)
	}

	if outcomes[0].Shown != 2 {
		t.Fatalf("viewer saw %d cards after BUILDING, want 2", outcomes[0].Shown)
	}
}

// Given a card that is not part of the generated population, when the viewer meets it, then
// it is counted and skipped. Deciding it would mean inventing a preference, and defaulting
// would quietly bias the outcome.
func TestUnknownCardsAreCountedNotDecided(t *testing.T) {
	agents := generate(t, testParams(20, 0.5))
	viewer := agents[0]
	viewer.Patience = 3
	fake := &fakeDeck{pages: [][]string{{agents[1].ID, "00000000-0000-4000-8000-000000000000", agents[2].ID}}}
	driver := driverFor(t, fake.server(t), agents)

	outcomes, err := driver.Run([]Agent{viewer})
	if err != nil {
		t.Fatalf("run: %v", err)
	}

	if outcomes[0].Unknown != 1 {
		t.Fatalf("unknown cards counted %d, want 1", outcomes[0].Unknown)
	}
	if outcomes[0].Shown != 3 {
		t.Fatalf("shown is %d, want 3 — an unknown card was still shown", outcomes[0].Shown)
	}
	if len(fake.swipes) != 2 {
		t.Fatalf("%d swipes posted, want 2 — the unknown card must not be swiped", len(fake.swipes))
	}
}

// Given a paginated deck, when patience allows, then the driver follows nextCursor.
func TestDriverFollowsPagination(t *testing.T) {
	agents := generate(t, testParams(20, 0.5))
	viewer := agents[0]
	viewer.Patience = 4
	fake := &fakeDeck{pages: [][]string{
		{agents[1].ID, agents[2].ID},
		{agents[3].ID, agents[4].ID},
	}}
	driver := driverFor(t, fake.server(t), agents)

	outcomes, err := driver.Run([]Agent{viewer})
	if err != nil {
		t.Fatalf("run: %v", err)
	}

	if outcomes[0].Shown != 4 {
		t.Fatalf("viewer saw %d cards across two pages, want 4", outcomes[0].Shown)
	}
}

// Given the same population, seed and deck, when the run is repeated, then the same cards
// are liked. Comparing two ranking variants must not also compare two sets of coin flips.
func TestDecisionsAreReproducible(t *testing.T) {
	// A dense population: with only a handful of agents spread over twelve cities the
	// nearest candidate is hundreds of kilometres away and nobody ever likes anybody, which
	// would make this test pass or fail on geography rather than on reproducibility.
	agents := generate(t, testParams(8_000, 0.5))
	viewer := agents[0]
	viewer.Patience = 20

	// A real deck never shows random strangers from the whole population: it shows
	// nearby, in-window candidates. Feeding random pairs here would only prove that the
	// distance penalty works.
	ids := plausibleDeck(viewer, agents, 20)

	liked := make([]int, 2)
	for run := 0; run < 2; run++ {
		fake := &fakeDeck{pages: [][]string{ids}}
		driver := driverFor(t, fake.server(t), agents)
		outcomes, err := driver.Run([]Agent{viewer})
		if err != nil {
			t.Fatalf("run %d: %v", run, err)
		}
		liked[run] = outcomes[0].Liked
	}

	if liked[0] != liked[1] {
		t.Fatalf("the same run liked %d then %d cards", liked[0], liked[1])
	}
	if liked[0] == 0 {
		t.Fatal("nothing was liked at all; the decision never fires and the outcome is degenerate")
	}
}

// Given an empty deck, when the viewer browses, then it stops without posting swipes.
func TestEmptyDeckEndsTheViewer(t *testing.T) {
	agents := generate(t, testParams(20, 0.5))
	viewer := agents[0]
	viewer.Patience = 10
	fake := &fakeDeck{pages: nil}
	driver := driverFor(t, fake.server(t), agents)

	outcomes, err := driver.Run([]Agent{viewer})
	if err != nil {
		t.Fatalf("run: %v", err)
	}

	if outcomes[0].Shown != 0 || len(fake.swipes) != 0 {
		t.Fatalf("empty deck produced %d shown and %d swipes", outcomes[0].Shown, len(fake.swipes))
	}
	if outcomes[0].DeckState != "EMPTY" {
		t.Fatalf("deck state recorded as %q, want EMPTY", outcomes[0].DeckState)
	}
}

// plausibleDeck picks the candidates closest to the viewer, which is roughly what any of
// the ranking variants surfaces. It exists so decision tests exercise the decision rather
// than the geography.
func plausibleDeck(viewer Agent, agents []Agent, size int) []string {
	type scored struct {
		id       string
		distance float64
	}
	candidates := make([]scored, 0, len(agents))
	for _, candidate := range agents {
		if candidate.ID == viewer.ID {
			continue
		}
		candidates = append(candidates, scored{candidate.ID, DistanceKm(viewer, candidate)})
	}
	sort.Slice(candidates, func(a, b int) bool { return candidates[a].distance < candidates[b].distance })

	if size > len(candidates) {
		size = len(candidates)
	}
	ids := make([]string, 0, size)
	for _, candidate := range candidates[:size] {
		ids = append(ids, candidate.id)
	}
	return ids
}
