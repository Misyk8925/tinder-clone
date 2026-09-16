package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sort"
	"strconv"
	"sync"
	"sync/atomic"
	"time"
)

// DeckCard is the subset of the v2 card this driver needs. The contract exposes name,
// city, bio, hobbies and photos too; none of them reach the decision.
type DeckCard struct {
	ProfileID string `json:"profileId"`
}

// DeckPage mirrors GET /api/v2/deck.
type DeckPage struct {
	Items      []DeckCard `json:"items"`
	NextCursor *string    `json:"nextCursor"`
	Generation int64      `json:"generation"`
	State      string     `json:"state"`
}

// Outcome is what one viewer produced. Counts only — no latent value appears here, so a
// metric computed from this file cannot leak the generator's ground truth (FR-14).
type Outcome struct {
	ViewerID   string `json:"viewerId"`
	Shown      int    `json:"shown"`
	Liked      int    `json:"liked"`
	Passed     int    `json:"passed"`
	Unknown    int    `json:"unknown"`
	Generation int64  `json:"generation"`
	DeckState  string `json:"deckState"`
}

type DriverConfig struct {
	BaseURL     string
	PageLimit   int
	Concurrency int
	BuildRetry  time.Duration
	BuildWaits  int
	Seed        int64
}

// Driver walks a set of viewers through their decks and swipes on what they were shown.
//
// Ranking reaches the outcome only because each agent has bounded Patience: without that
// bound every viewer would eventually see every candidate and the order would not matter.
type Driver struct {
	config    DriverConfig
	tokens    TokenSource
	client    *http.Client
	byID      map[string]Agent
	requests  atomic.Int64
	swipesOut atomic.Int64
}

func NewDriver(config DriverConfig, tokens TokenSource, agents []Agent) *Driver {
	byID := make(map[string]Agent, len(agents))
	for _, agent := range agents {
		byID[agent.ID] = agent
	}
	return &Driver{
		config: config,
		tokens: tokens,
		// One shared client so connections are reused; a fresh handshake per request
		// would land in the latency this experiment compares.
		client: &http.Client{Timeout: 30 * time.Second},
		byID:   byID,
	}
}

func (d *Driver) Run(viewers []Agent) ([]Outcome, error) {
	concurrency := d.config.Concurrency
	if concurrency <= 0 {
		concurrency = 1
	}

	outcomes := make([]Outcome, len(viewers))
	failures := make([]error, len(viewers))
	work := make(chan int)
	var waitGroup sync.WaitGroup

	for worker := 0; worker < concurrency; worker++ {
		waitGroup.Add(1)
		go func() {
			defer waitGroup.Done()
			for index := range work {
				outcome, err := d.runViewer(viewers[index])
				outcomes[index] = outcome
				failures[index] = err
			}
		}()
	}
	for index := range viewers {
		work <- index
	}
	close(work)
	waitGroup.Wait()

	for index, err := range failures {
		if err != nil {
			return outcomes, fmt.Errorf("viewer %s: %w", viewers[index].ID, err)
		}
	}
	return outcomes, nil
}

func (d *Driver) runViewer(viewer Agent) (Outcome, error) {
	outcome := Outcome{ViewerID: viewer.ID}

	token, err := d.tokens.Token(viewer.ID)
	if err != nil {
		return outcome, err
	}

	var cursor *string
	remaining := viewer.Patience

	for remaining > 0 {
		page, err := d.fetchDeck(token, cursor)
		if err != nil {
			return outcome, err
		}
		outcome.Generation = page.Generation
		outcome.DeckState = page.State

		if len(page.Items) == 0 {
			break
		}

		for _, card := range page.Items {
			if remaining == 0 {
				break
			}
			remaining--
			outcome.Shown++

			candidate, known := d.byID[card.ProfileID]
			if !known {
				// A card outside the generated population cannot be decided without
				// inventing a preference. Counted and skipped rather than defaulted,
				// because a default would quietly bias the outcome.
				outcome.Unknown++
				continue
			}

			decision := d.decide(viewer, candidate)
			if err := d.postSwipe(token, viewer.ID, candidate.ID, decision); err != nil {
				return outcome, err
			}
			if decision {
				outcome.Liked++
			} else {
				outcome.Passed++
			}
		}

		if page.NextCursor == nil {
			break
		}
		cursor = page.NextCursor
	}

	return outcome, nil
}

// decide draws from the latent model. The draw is seeded from the pair, so a re-run of the
// same population and seed produces the same swipes.
func (d *Driver) decide(viewer, candidate Agent) bool {
	probability := LikeProbability(viewer, candidate)
	return pairDraw(viewer.ID, candidate.ID, d.config.Seed) < probability
}

func (d *Driver) fetchDeck(token string, cursor *string) (DeckPage, error) {
	url := d.config.BaseURL + "/api/v2/deck?limit=" + strconv.Itoa(d.config.PageLimit)
	if cursor != nil {
		url += "&cursor=" + *cursor
	}

	for attempt := 0; ; attempt++ {
		request, err := http.NewRequest(http.MethodGet, url, nil)
		if err != nil {
			return DeckPage{}, err
		}
		request.Header.Set("Authorization", "Bearer "+token)

		response, err := d.client.Do(request)
		if err != nil {
			return DeckPage{}, err
		}
		d.requests.Add(1)
		body, readErr := io.ReadAll(response.Body)
		response.Body.Close()
		if readErr != nil {
			return DeckPage{}, readErr
		}

		switch response.StatusCode {
		case http.StatusOK:
			var page DeckPage
			if err := json.Unmarshal(body, &page); err != nil {
				return DeckPage{}, fmt.Errorf("decode deck page: %w", err)
			}
			return page, nil
		case http.StatusAccepted:
			// 202 BUILDING: the read model is still materialising this viewer's deck.
			if attempt >= d.config.BuildWaits {
				return DeckPage{State: "BUILDING"}, nil
			}
			time.Sleep(d.config.BuildRetry)
		default:
			return DeckPage{}, fmt.Errorf("deck returned %d: %s", response.StatusCode, truncate(body))
		}
	}
}

func (d *Driver) postSwipe(token, viewerID, candidateID string, decision bool) error {
	payload, err := json.Marshal(map[string]any{
		"profile1Id": viewerID,
		"profile2Id": candidateID,
		"decision":   decision,
		"isSuper":    false,
	})
	if err != nil {
		return err
	}

	request, err := http.NewRequest(http.MethodPost, d.config.BaseURL+"/api/v1/swipes", bytes.NewReader(payload))
	if err != nil {
		return err
	}
	request.Header.Set("Authorization", "Bearer "+token)
	request.Header.Set("Content-Type", "application/json")

	response, err := d.client.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	body, _ := io.ReadAll(response.Body)
	d.requests.Add(1)

	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return fmt.Errorf("swipe returned %d: %s", response.StatusCode, truncate(body))
	}
	d.swipesOut.Add(1)
	return nil
}

func (d *Driver) Stats() (requests int64, swipes int64) {
	return d.requests.Load(), d.swipesOut.Load()
}

// SelectViewers picks the agents that will actually browse. The population is large so the
// candidate pool is realistic; the active set can be far smaller, which is also how a real
// product behaves.
func SelectViewers(agents []Agent, count int) []Agent {
	if count <= 0 || count >= len(agents) {
		return agents
	}
	selected := append([]Agent(nil), agents[:count]...)
	sort.Slice(selected, func(a, b int) bool { return selected[a].ID < selected[b].ID })
	return selected
}

func truncate(body []byte) string {
	const limit = 200
	if len(body) > limit {
		return string(body[:limit])
	}
	return string(body)
}
