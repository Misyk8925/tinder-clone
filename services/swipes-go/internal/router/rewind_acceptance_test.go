package router

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"
	"tinder-clone/services/swipes-go/internal/metrics"
	"tinder-clone/services/swipes-go/internal/model"
	"tinder-clone/services/swipes-go/internal/security"
	"tinder-clone/services/swipes-go/internal/service"
)

// Feature: Swipes Rewind HTTP boundary (FR-1..FR-6, FR-9, NFR-3, NFR-4, and all Phase-2 HTTP error rows).
// Exercises the real fasthttp router + real service.Swipes against a faithful in-memory rewind store.

type rewindErrorBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type fakeSwipeProducer struct {
	commands []model.SwipeCommand
	revoked  []model.SwipeRevokedEvent
	revErr   error
}

func (p *fakeSwipeProducer) Send(_ context.Context, cmd model.SwipeCommand) error {
	p.commands = append(p.commands, cmd)
	return nil
}

func (p *fakeSwipeProducer) PublishRevoked(_ context.Context, ev model.SwipeRevokedEvent) error {
	if p.revErr != nil {
		return p.revErr
	}
	p.revoked = append(p.revoked, ev)
	return nil
}

type fakeProfileLookup struct {
	owner uuid.UUID
	err   error
}

func (f fakeProfileLookup) ExistsAll(context.Context, uuid.UUID, uuid.UUID, string) (bool, error) {
	return true, nil
}

func (f fakeProfileLookup) ProfileIDForUser(context.Context, string, string) (uuid.UUID, error) {
	return f.owner, f.err
}

type inMemoryRewindStore struct {
	mu          sync.Mutex
	history     []model.SwipeHistoryRecord
	revocations map[uuid.UUID]model.RewindResponse
	matches     map[string]bool
	storeErr    error
}

func newInMemoryRewindStore() *inMemoryRewindStore {
	return &inMemoryRewindStore{
		revocations: make(map[uuid.UUID]model.RewindResponse),
		matches:     make(map[string]bool),
	}
}

func (s *inMemoryRewindStore) RecordSwipe(_ context.Context, rec model.SwipeHistoryRecord) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.storeErr != nil {
		return s.storeErr
	}
	s.history = append(s.history, rec)
	return nil
}

func (s *inMemoryRewindStore) addMatch(a, b uuid.UUID) {
	s.mu.Lock()
	defer s.mu.Unlock()
	key := pairKey(a, b)
	s.matches[key] = true
}

func (s *inMemoryRewindStore) RewindLastSwipe(
	ctx context.Context,
	profile1ID uuid.UUID,
	expectedSwipeEventID *uuid.UUID,
	now time.Time,
	window time.Duration,
	publish func(context.Context, model.SwipeRevokedEvent) error,
) (model.RewindResponse, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.storeErr != nil {
		return model.RewindResponse{}, model.HTTPError{Status: http.StatusServiceUnavailable, Code: "SERVICE_UNAVAILABLE", Reason: "Rewind store unavailable"}
	}
	var latest *model.SwipeHistoryRecord
	for i := range s.history {
		if s.history[i].Profile1ID == profile1ID {
			if latest == nil || !s.history[i].SwipedAt.Before(latest.SwipedAt) {
				rec := s.history[i]
				latest = &rec
			}
		}
	}
	if latest == nil {
		return model.RewindResponse{}, model.HTTPError{Status: http.StatusNotFound, Code: "NO_SWIPE_TO_REWIND", Reason: "No swipe found to rewind"}
	}
	if expectedSwipeEventID != nil && *expectedSwipeEventID != latest.EventID {
		return model.RewindResponse{}, model.HTTPError{Status: http.StatusConflict, Code: "NOT_LATEST_SWIPE", Reason: "Provided swipeEventId is not the latest swipe"}
	}
	if prev, ok := s.revocations[latest.EventID]; ok {
		prev.AlreadyRevoked = true
		return prev, nil
	}
	if now.Sub(latest.SwipedAt) > window {
		return model.RewindResponse{}, model.HTTPError{Status: http.StatusConflict, Code: "REWIND_WINDOW_EXPIRED", Reason: "Last swipe can only be rewound within 5 minutes"}
	}
	if s.matches[pairKey(latest.Profile1ID, latest.Profile2ID)] {
		return model.RewindResponse{}, model.HTTPError{Status: http.StatusConflict, Code: "SWIPE_ALREADY_MATCHED", Reason: "Cannot rewind a swipe that already resulted in a match"}
	}
	revID := uuid.New()
	event := model.SwipeRevokedEvent{
		EventID:             revID.String(),
		RevokedSwipeEventID: latest.EventID.String(),
		Profile1ID:          latest.Profile1ID.String(),
		Profile2ID:          latest.Profile2ID.String(),
		OriginalDecision:    latest.Decision,
		SwipedAt:            latest.SwipedAt.UTC().UnixMilli(),
		RevokedAt:           now.UTC().UnixMilli(),
	}
	if err := publish(ctx, event); err != nil {
		return model.RewindResponse{}, model.HTTPError{Status: http.StatusServiceUnavailable, Code: "SERVICE_UNAVAILABLE", Reason: "Rewind event publisher is temporarily unavailable"}
	}
	resp := model.RewindResponse{
		RevokedSwipeEventID: latest.EventID.String(),
		RevocationEventID:   revID.String(),
		Profile2ID:          latest.Profile2ID.String(),
		Decision:            latest.Decision,
		RevokedAt:           now.UTC(),
		AlreadyRevoked:      false,
	}
	s.revocations[latest.EventID] = resp
	return resp, nil
}

func pairKey(a, b uuid.UUID) string {
	if a.String() < b.String() {
		return a.String() + ":" + b.String()
	}
	return b.String() + ":" + a.String()
}

func newRewindHarness(owner uuid.UUID, lookupErr error, store *inMemoryRewindStore, producer *fakeSwipeProducer, clock func() time.Time) (*Router, *metrics.Metrics) {
	m := &metrics.Metrics{}
	svc := service.New(producer, fakeProfileLookup{owner: owner, err: lookupErr}, m, false).
		WithRewind(store, producer, 5*time.Minute, clock)
	return New(svc, security.NewAuthenticator("secret", nil), m, fakeQueue{}, testLogger()), m
}

func TestFeatureSwipeRewindHTTPBoundary(t *testing.T) {
	baseTime := time.Date(2026, 11, 25, 21, 50, 0, 0, time.UTC)

	t.Run("Scenario: Given an unauthenticated request, when POST /api/v1/swipes/rewind is called, then 401 UNAUTHENTICATED is returned", func(t *testing.T) {
		// Given
		r, _ := newRewindHarness(uuid.New(), nil, newInMemoryRewindStore(), &fakeSwipeProducer{}, func() time.Time { return baseTime })
		ctx := requestContext(http.MethodPost, "/api/v1/swipes/rewind", []byte(`{}`))

		// When
		r.Handler(ctx)

		// Then
		if ctx.Response.StatusCode() != http.StatusUnauthorized {
			t.Fatalf("expected 401 UNAUTHENTICATED, got status=%d body=%s", ctx.Response.StatusCode(), ctx.Response.Body())
		}
		assertRewindErrorCode(t, ctx.Response.Body(), "UNAUTHENTICATED")
	})

	t.Run("Scenario: Given malformed JSON body, when POST /api/v1/swipes/rewind is called, then 400 INVALID_PAYLOAD is returned", func(t *testing.T) {
		// Given
		r, _ := newRewindHarness(uuid.New(), nil, newInMemoryRewindStore(), &fakeSwipeProducer{}, func() time.Time { return baseTime })
		ctx := requestContext(http.MethodPost, "/api/v1/swipes/rewind", []byte(`{"swipeEventId":"not-a-uuid"}`))
		ctx.Request.Header.Set("X-Internal-Auth", "secret")

		// When
		r.Handler(ctx)

		// Then
		if ctx.Response.StatusCode() != http.StatusBadRequest {
			t.Fatalf("expected 400 INVALID_PAYLOAD, got status=%d body=%s", ctx.Response.StatusCode(), ctx.Response.Body())
		}
		assertRewindErrorCode(t, ctx.Response.Body(), "INVALID_PAYLOAD")
	})

	t.Run("Scenario: Given a non-POST method on /api/v1/swipes/rewind, when requested, then 405 Method Not Allowed is returned", func(t *testing.T) {
		// Given
		r, _ := newRewindHarness(uuid.New(), nil, newInMemoryRewindStore(), &fakeSwipeProducer{}, func() time.Time { return baseTime })
		ctx := requestContext(http.MethodGet, "/api/v1/swipes/rewind", nil)

		// When
		r.Handler(ctx)

		// Then
		if ctx.Response.StatusCode() != http.StatusMethodNotAllowed {
			t.Fatalf("expected 405 Method Not Allowed, got status=%d body=%s", ctx.Response.StatusCode(), ctx.Response.Body())
		}
	})

	t.Run("Scenario: Given a latest swipe within 5 minutes (FR-1, FR-6), when POST /api/v1/swipes/rewind is called, then 200 OK returns revokedSwipeEventId, revocationEventId, profile2Id, and alreadyRevoked=false", func(t *testing.T) {
		// Given
		viewer, target := uuid.New(), uuid.New()
		swipeID := uuid.New()
		store := newInMemoryRewindStore()
		_ = store.RecordSwipe(context.Background(), model.SwipeHistoryRecord{
			EventID: swipeID, Profile1ID: viewer, Profile2ID: target, Decision: false, SwipedAt: baseTime,
		})
		producer := &fakeSwipeProducer{}
		r, m := newRewindHarness(viewer, nil, store, producer, func() time.Time { return baseTime.Add(2 * time.Minute) })
		ctx := requestContext(http.MethodPost, "/api/v1/swipes/rewind", []byte(`{}`))
		ctx.Request.Header.Set("X-Internal-Auth", "secret")

		// When
		r.Handler(ctx)

		// Then
		if ctx.Response.StatusCode() != http.StatusOK {
			t.Fatalf("expected 200 OK for FR-1 rewind, got status=%d body=%s", ctx.Response.StatusCode(), ctx.Response.Body())
		}
		var resp model.RewindResponse
		if err := json.Unmarshal(ctx.Response.Body(), &resp); err != nil {
			t.Fatalf("invalid JSON response: %v", err)
		}
		if resp.RevokedSwipeEventID != swipeID.String() || resp.Profile2ID != target.String() || resp.AlreadyRevoked || resp.RevocationEventID == "" {
			t.Fatalf("unexpected rewind response: %+v", resp)
		}
		if len(producer.revoked) != 1 || producer.revoked[0].Profile1ID != viewer.String() || producer.revoked[0].RevokedSwipeEventID != swipeID.String() {
			t.Fatalf("expected 1 SwipeRevoked event, got %+v", producer.revoked)
		}
		if len(store.history) != 1 {
			t.Fatalf("original swipe history row must never be deleted (FR-6)")
		}
		if m.Revoked.Load() != 1 {
			t.Fatalf("expected swipes_revoked_total=1, got %d", m.Revoked.Load())
		}
	})

	t.Run("Scenario: Given the latest swipe was already rewound (FR-2, NFR-3), when POST /api/v1/swipes/rewind is called again, then 200 OK returns alreadyRevoked=true without undoing an earlier swipe", func(t *testing.T) {
		// Given
		viewer, olderTarget, latestTarget := uuid.New(), uuid.New(), uuid.New()
		olderID, latestID := uuid.New(), uuid.New()
		store := newInMemoryRewindStore()
		_ = store.RecordSwipe(context.Background(), model.SwipeHistoryRecord{
			EventID: olderID, Profile1ID: viewer, Profile2ID: olderTarget, Decision: true, SwipedAt: baseTime,
		})
		_ = store.RecordSwipe(context.Background(), model.SwipeHistoryRecord{
			EventID: latestID, Profile1ID: viewer, Profile2ID: latestTarget, Decision: false, SwipedAt: baseTime.Add(10 * time.Second),
		})
		producer := &fakeSwipeProducer{}
		r, _ := newRewindHarness(viewer, nil, store, producer, func() time.Time { return baseTime.Add(time.Minute) })

		firstCtx := requestContext(http.MethodPost, "/api/v1/swipes/rewind", nil)
		firstCtx.Request.Header.Set("X-Internal-Auth", "secret")
		r.Handler(firstCtx)
		var firstResp model.RewindResponse
		_ = json.Unmarshal(firstCtx.Response.Body(), &firstResp)

		// When: second rewind call
		secondCtx := requestContext(http.MethodPost, "/api/v1/swipes/rewind", []byte(`{}`))
		secondCtx.Request.Header.Set("X-Internal-Auth", "secret")
		r.Handler(secondCtx)

		// Then
		if secondCtx.Response.StatusCode() != http.StatusOK {
			t.Fatalf("expected 200 OK idempotent rewind (FR-2), got status=%d body=%s", secondCtx.Response.StatusCode(), secondCtx.Response.Body())
		}
		var secondResp model.RewindResponse
		_ = json.Unmarshal(secondCtx.Response.Body(), &secondResp)
		if !secondResp.AlreadyRevoked || secondResp.RevokedSwipeEventID != latestID.String() || secondResp.RevocationEventID != firstResp.RevocationEventID {
			t.Fatalf("expected idempotent response for latest swipe, got %+v", secondResp)
		}
		if len(producer.revoked) != 1 {
			t.Fatalf("expected exactly 1 SwipeRevoked event across repeated calls, got %d", len(producer.revoked))
		}
	})

	t.Run("Scenario: Given the latest swipe is older than 5 minutes (FR-3), when POST /api/v1/swipes/rewind is called, then 409 REWIND_WINDOW_EXPIRED is returned", func(t *testing.T) {
		// Given
		viewer, target := uuid.New(), uuid.New()
		store := newInMemoryRewindStore()
		_ = store.RecordSwipe(context.Background(), model.SwipeHistoryRecord{
			EventID: uuid.New(), Profile1ID: viewer, Profile2ID: target, Decision: true, SwipedAt: baseTime,
		})
		producer := &fakeSwipeProducer{}
		r, _ := newRewindHarness(viewer, nil, store, producer, func() time.Time { return baseTime.Add(5*time.Minute + time.Second) })
		ctx := requestContext(http.MethodPost, "/api/v1/swipes/rewind", []byte(`{}`))
		ctx.Request.Header.Set("X-Internal-Auth", "secret")

		// When
		r.Handler(ctx)

		// Then
		if ctx.Response.StatusCode() != http.StatusConflict {
			t.Fatalf("expected 409 REWIND_WINDOW_EXPIRED (FR-3), got status=%d body=%s", ctx.Response.StatusCode(), ctx.Response.Body())
		}
		assertRewindErrorCode(t, ctx.Response.Body(), "REWIND_WINDOW_EXPIRED")
		if len(producer.revoked) != 0 {
			t.Fatalf("expired rewind must publish 0 events")
		}
	})

	t.Run("Scenario: Given the latest swipe already matched (FR-4), when POST /api/v1/swipes/rewind is called, then 409 SWIPE_ALREADY_MATCHED is returned", func(t *testing.T) {
		// Given
		viewer, target := uuid.New(), uuid.New()
		store := newInMemoryRewindStore()
		_ = store.RecordSwipe(context.Background(), model.SwipeHistoryRecord{
			EventID: uuid.New(), Profile1ID: viewer, Profile2ID: target, Decision: true, SwipedAt: baseTime,
		})
		store.addMatch(viewer, target)
		producer := &fakeSwipeProducer{}
		r, _ := newRewindHarness(viewer, nil, store, producer, func() time.Time { return baseTime.Add(30 * time.Second) })
		ctx := requestContext(http.MethodPost, "/api/v1/swipes/rewind", []byte(`{}`))
		ctx.Request.Header.Set("X-Internal-Auth", "secret")

		// When
		r.Handler(ctx)

		// Then
		if ctx.Response.StatusCode() != http.StatusConflict {
			t.Fatalf("expected 409 SWIPE_ALREADY_MATCHED (FR-4), got status=%d body=%s", ctx.Response.StatusCode(), ctx.Response.Body())
		}
		assertRewindErrorCode(t, ctx.Response.Body(), "SWIPE_ALREADY_MATCHED")
		if len(producer.revoked) != 0 {
			t.Fatalf("matched rewind must publish 0 events")
		}
	})

	t.Run("Scenario: Given no prior swipes exist (FR-5), when POST /api/v1/swipes/rewind is called, then 404 NO_SWIPE_TO_REWIND is returned", func(t *testing.T) {
		// Given
		r, _ := newRewindHarness(uuid.New(), nil, newInMemoryRewindStore(), &fakeSwipeProducer{}, func() time.Time { return baseTime })
		ctx := requestContext(http.MethodPost, "/api/v1/swipes/rewind", []byte(`{}`))
		ctx.Request.Header.Set("X-Internal-Auth", "secret")

		// When
		r.Handler(ctx)

		// Then
		if ctx.Response.StatusCode() != http.StatusNotFound {
			t.Fatalf("expected 404 NO_SWIPE_TO_REWIND (FR-5), got status=%d body=%s", ctx.Response.StatusCode(), ctx.Response.Body())
		}
		assertRewindErrorCode(t, ctx.Response.Body(), "NO_SWIPE_TO_REWIND")
	})

	t.Run("Scenario: Given optional swipeEventId does not match latest swipe (FR-5), when POST /api/v1/swipes/rewind is called, then 409 NOT_LATEST_SWIPE is returned", func(t *testing.T) {
		// Given
		viewer, target := uuid.New(), uuid.New()
		store := newInMemoryRewindStore()
		_ = store.RecordSwipe(context.Background(), model.SwipeHistoryRecord{
			EventID: uuid.New(), Profile1ID: viewer, Profile2ID: target, Decision: false, SwipedAt: baseTime,
		})
		r, _ := newRewindHarness(viewer, nil, store, &fakeSwipeProducer{}, func() time.Time { return baseTime.Add(time.Minute) })
		ctx := requestContext(http.MethodPost, "/api/v1/swipes/rewind", []byte(`{"swipeEventId":"8c64d80e-4d5b-4df3-8b1e-1d8a3f9520b4"}`))
		ctx.Request.Header.Set("X-Internal-Auth", "secret")

		// When
		r.Handler(ctx)

		// Then
		if ctx.Response.StatusCode() != http.StatusConflict {
			t.Fatalf("expected 409 NOT_LATEST_SWIPE (FR-5), got status=%d body=%s", ctx.Response.StatusCode(), ctx.Response.Body())
		}
		assertRewindErrorCode(t, ctx.Response.Body(), "NOT_LATEST_SWIPE")
	})

	t.Run("Scenario: Given authenticated user has no owned profile (NFR-4), when POST /api/v1/swipes/rewind is called, then 403 PROFILE_OWNERSHIP_DENIED is returned", func(t *testing.T) {
		// Given
		r, _ := newRewindHarness(uuid.Nil, model.ErrProfileNotFound, newInMemoryRewindStore(), &fakeSwipeProducer{}, func() time.Time { return baseTime })
		ctx := requestContext(http.MethodPost, "/api/v1/swipes/rewind", []byte(`{}`))
		ctx.Request.Header.Set("X-Internal-Auth", "secret")

		// When
		r.Handler(ctx)

		// Then
		if ctx.Response.StatusCode() != http.StatusForbidden {
			t.Fatalf("expected 403 PROFILE_OWNERSHIP_DENIED, got status=%d body=%s", ctx.Response.StatusCode(), ctx.Response.Body())
		}
		assertRewindErrorCode(t, ctx.Response.Body(), "PROFILE_OWNERSHIP_DENIED")
	})

	t.Run("Scenario: Given downstream store or Kafka producer fails, when POST /api/v1/swipes/rewind is called, then 503 SERVICE_UNAVAILABLE is returned", func(t *testing.T) {
		// Given
		viewer, target := uuid.New(), uuid.New()
		store := newInMemoryRewindStore()
		_ = store.RecordSwipe(context.Background(), model.SwipeHistoryRecord{
			EventID: uuid.New(), Profile1ID: viewer, Profile2ID: target, Decision: false, SwipedAt: baseTime,
		})
		producer := &fakeSwipeProducer{revErr: errors.New("kafka broker down")}
		r, _ := newRewindHarness(viewer, nil, store, producer, func() time.Time { return baseTime.Add(time.Minute) })
		ctx := requestContext(http.MethodPost, "/api/v1/swipes/rewind", []byte(`{}`))
		ctx.Request.Header.Set("X-Internal-Auth", "secret")

		// When
		r.Handler(ctx)

		// Then
		if ctx.Response.StatusCode() != http.StatusServiceUnavailable {
			t.Fatalf("expected 503 SERVICE_UNAVAILABLE, got status=%d body=%s", ctx.Response.StatusCode(), ctx.Response.Body())
		}
		assertRewindErrorCode(t, ctx.Response.Body(), "SERVICE_UNAVAILABLE")
	})
}

func assertRewindErrorCode(t *testing.T, body []byte, wantCode string) {
	t.Helper()
	var parsed rewindErrorBody
	if err := json.Unmarshal(body, &parsed); err != nil {
		t.Fatalf("expected JSON RewindErrorResponse with code=%s, got non-JSON body=%q: %v", wantCode, string(body), err)
	}
	if parsed.Code != wantCode {
		t.Fatalf("expected error code=%s, got %q (body=%s)", wantCode, parsed.Code, string(body))
	}
}
