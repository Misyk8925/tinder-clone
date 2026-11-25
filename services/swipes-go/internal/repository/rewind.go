package repository

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"tinder-clone/services/swipes-go/internal/model"
)

type RewindStore struct {
	db *pgxpool.Pool
}

func NewRewindStore(ctx context.Context, db *pgxpool.Pool) (*RewindStore, error) {
	if db == nil {
		return nil, fmt.Errorf("PostgreSQL pool is required for rewind store")
	}
	checkCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	for _, table := range []string{"public.swipe_history", "public.swipe_revocations", "public.swipe_matches"} {
		var name *string
		if err := db.QueryRow(checkCtx, `SELECT to_regclass($1)::text`, table).Scan(&name); err != nil || name == nil {
			return nil, fmt.Errorf("central migration V3_swipes_rewind.sql is not applied (missing %s)", table)
		}
	}
	return &RewindStore{db: db}, nil
}

func (store *RewindStore) RecordSwipe(ctx context.Context, record model.SwipeHistoryRecord) error {
	dbCtx, cancel := context.WithTimeout(ctx, 2*time.Second)
	defer cancel()
	_, err := store.db.Exec(dbCtx, `
		INSERT INTO swipe_history (event_id, profile1_id, profile2_id, decision, is_super, swiped_at)
		VALUES ($1, $2, $3, $4, $5, $6)
		ON CONFLICT (event_id) DO NOTHING
	`, record.EventID, record.Profile1ID, record.Profile2ID, record.Decision, record.IsSuper, record.SwipedAt.UTC())
	return err
}

func (store *RewindStore) SaveMatch(ctx context.Context, event model.MatchCreatedEvent) error {
	p1, err := uuid.Parse(event.Profile1ID)
	if err != nil {
		return fmt.Errorf("invalid match profile1Id: %w", err)
	}
	p2, err := uuid.Parse(event.Profile2ID)
	if err != nil {
		return fmt.Errorf("invalid match profile2Id: %w", err)
	}
	if p1 == p2 {
		return fmt.Errorf("match profile IDs must be different")
	}
	left, right := normalizePair(p1, p2)
	matchedAt := time.Now().UTC()
	if event.CreatedAt != nil && !event.CreatedAt.Time.IsZero() {
		matchedAt = event.CreatedAt.Time.UTC()
	}
	dbCtx, cancel := context.WithTimeout(ctx, 2*time.Second)
	defer cancel()
	_, err = store.db.Exec(dbCtx, `
		INSERT INTO swipe_matches (profile1_id, profile2_id, matched_at)
		VALUES ($1, $2, $3)
		ON CONFLICT (profile1_id, profile2_id) DO NOTHING
	`, left, right, matchedAt)
	return err
}

func (store *RewindStore) RewindLastSwipe(
	ctx context.Context,
	profile1ID uuid.UUID,
	expectedSwipeEventID *uuid.UUID,
	now time.Time,
	window time.Duration,
	publish func(context.Context, model.SwipeRevokedEvent) error,
) (model.RewindResponse, error) {
	txCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	tx, err := store.db.BeginTx(txCtx, pgx.TxOptions{})
	if err != nil {
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusServiceUnavailable,
			Code:   "SERVICE_UNAVAILABLE",
			Reason: "Rewind store is temporarily unavailable",
		}
	}
	defer func() { _ = tx.Rollback(txCtx) }()

	// Serialize concurrent rewinds for the same viewer so duplicate clicks cannot emit two events.
	if _, err := tx.Exec(txCtx, `SELECT pg_advisory_xact_lock(hashtext($1))`, profile1ID.String()); err != nil {
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusServiceUnavailable,
			Code:   "SERVICE_UNAVAILABLE",
			Reason: "Rewind store is temporarily unavailable",
		}
	}

	var latest model.SwipeHistoryRecord
	err = tx.QueryRow(txCtx, `
		SELECT event_id, profile1_id, profile2_id, decision, is_super, swiped_at
		FROM swipe_history
		WHERE profile1_id = $1
		ORDER BY swiped_at DESC, event_id DESC
		LIMIT 1
	`, profile1ID).Scan(&latest.EventID, &latest.Profile1ID, &latest.Profile2ID, &latest.Decision, &latest.IsSuper, &latest.SwipedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusNotFound,
			Code:   "NO_SWIPE_TO_REWIND",
			Reason: "No swipe found to rewind",
		}
	}
	if err != nil {
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusServiceUnavailable,
			Code:   "SERVICE_UNAVAILABLE",
			Reason: "Rewind store is temporarily unavailable",
		}
	}

	if expectedSwipeEventID != nil && *expectedSwipeEventID != latest.EventID {
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusConflict,
			Code:   "NOT_LATEST_SWIPE",
			Reason: "Provided swipeEventId is not the latest swipe",
		}
	}

	var existingRevocationID uuid.UUID
	var existingRevokedAt time.Time
	err = tx.QueryRow(txCtx, `
		SELECT revocation_event_id, revoked_at
		FROM swipe_revocations
		WHERE revoked_swipe_event_id = $1
	`, latest.EventID).Scan(&existingRevocationID, &existingRevokedAt)
	if err == nil {
		return model.RewindResponse{
			RevokedSwipeEventID: latest.EventID.String(),
			RevocationEventID:   existingRevocationID.String(),
			Profile2ID:          latest.Profile2ID.String(),
			Decision:            latest.Decision,
			RevokedAt:           existingRevokedAt.UTC(),
			AlreadyRevoked:      true,
		}, nil
	}
	if !errors.Is(err, pgx.ErrNoRows) {
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusServiceUnavailable,
			Code:   "SERVICE_UNAVAILABLE",
			Reason: "Rewind store is temporarily unavailable",
		}
	}

	if now.UTC().Sub(latest.SwipedAt.UTC()) > window {
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusConflict,
			Code:   "REWIND_WINDOW_EXPIRED",
			Reason: "Last swipe can only be rewound within 5 minutes",
		}
	}

	left, right := normalizePair(latest.Profile1ID, latest.Profile2ID)
	var matched bool
	err = tx.QueryRow(txCtx, `
		SELECT EXISTS(SELECT 1 FROM swipe_matches WHERE profile1_id = $1 AND profile2_id = $2)
	`, left, right).Scan(&matched)
	if err != nil {
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusServiceUnavailable,
			Code:   "SERVICE_UNAVAILABLE",
			Reason: "Rewind store is temporarily unavailable",
		}
	}
	if matched {
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusConflict,
			Code:   "SWIPE_ALREADY_MATCHED",
			Reason: "Cannot rewind a swipe that already resulted in a match",
		}
	}

	revocationID := uuid.UUID(model.NewEventID())
	revokedAt := now.UTC()
	if _, err := tx.Exec(txCtx, `
		INSERT INTO swipe_revocations (
			revocation_event_id, revoked_swipe_event_id, profile1_id, profile2_id,
			original_decision, swiped_at, revoked_at
		) VALUES ($1, $2, $3, $4, $5, $6, $7)
	`, revocationID, latest.EventID, latest.Profile1ID, latest.Profile2ID, latest.Decision, latest.SwipedAt.UTC(), revokedAt); err != nil {
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusServiceUnavailable,
			Code:   "SERVICE_UNAVAILABLE",
			Reason: "Rewind store is temporarily unavailable",
		}
	}

	event := model.SwipeRevokedEvent{
		EventID:             revocationID.String(),
		RevokedSwipeEventID: latest.EventID.String(),
		Profile1ID:          latest.Profile1ID.String(),
		Profile2ID:          latest.Profile2ID.String(),
		OriginalDecision:    latest.Decision,
		SwipedAt:            latest.SwipedAt.UTC().UnixMilli(),
		RevokedAt:           revokedAt.UnixMilli(),
	}
	if err := publish(txCtx, event); err != nil {
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusServiceUnavailable,
			Code:   "SERVICE_UNAVAILABLE",
			Reason: "Rewind event publisher is temporarily unavailable",
		}
	}

	if err := tx.Commit(txCtx); err != nil {
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusServiceUnavailable,
			Code:   "SERVICE_UNAVAILABLE",
			Reason: "Rewind store commit failed",
		}
	}

	return model.RewindResponse{
		RevokedSwipeEventID: latest.EventID.String(),
		RevocationEventID:   revocationID.String(),
		Profile2ID:          latest.Profile2ID.String(),
		Decision:            latest.Decision,
		RevokedAt:           revokedAt,
		AlreadyRevoked:      false,
	}, nil
}

func normalizePair(a, b uuid.UUID) (uuid.UUID, uuid.UUID) {
	if a.String() < b.String() {
		return a, b
	}
	return b, a
}
