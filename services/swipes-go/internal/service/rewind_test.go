package service

import (
	"context"
	"net/http"
	"testing"
	"time"

	"github.com/google/uuid"
	"tinder-clone/services/swipes-go/internal/metrics"
	"tinder-clone/services/swipes-go/internal/model"
)

type recordingRewindStore struct {
	recorded []model.SwipeHistoryRecord
	resp     model.RewindResponse
	err      error
}

func (s *recordingRewindStore) RecordSwipe(_ context.Context, rec model.SwipeHistoryRecord) error {
	s.recorded = append(s.recorded, rec)
	return nil
}

func (s *recordingRewindStore) RewindLastSwipe(
	_ context.Context,
	_ uuid.UUID,
	_ *uuid.UUID,
	_ time.Time,
	_ time.Duration,
	_ func(context.Context, model.SwipeRevokedEvent) error,
) (model.RewindResponse, error) {
	return s.resp, s.err
}

type noopRevokedPublisher struct{}

func (noopRevokedPublisher) PublishRevoked(context.Context, model.SwipeRevokedEvent) error {
	return nil
}

func TestSendRecordsHistoryWithSameEventIDAsKafkaCommand(t *testing.T) {
	first, second := uuid.New(), uuid.New()
	producer := &fakeProducer{}
	store := &recordingRewindStore{}
	fixedTime := time.Date(2026, 11, 25, 22, 0, 0, 0, time.UTC)

	sut := New(producer, fakeProfiles{owner: first, exists: true}, &metrics.Metrics{}, false).
		WithRewind(store, noopRevokedPublisher{}, 5*time.Minute, func() time.Time { return fixedTime })

	if err := sut.Send(context.Background(), swipe(first, second, false), userPrincipal(), false); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(store.recorded) != 1 || len(producer.commands) != 1 {
		t.Fatalf("expected 1 history record and 1 kafka command, got %d and %d", len(store.recorded), len(producer.commands))
	}
	if store.recorded[0].EventID != uuid.UUID(producer.commands[0].EventID) {
		t.Fatalf("history eventId %s must match kafka SwipeCommand eventId %s", store.recorded[0].EventID, uuid.UUID(producer.commands[0].EventID))
	}
	if producer.commands[0].Timestamp != fixedTime.UnixMilli() {
		t.Fatalf("expected timestamp %d, got %d", fixedTime.UnixMilli(), producer.commands[0].Timestamp)
	}
}

func TestRewindRejectsNilOwnerProfileWith403(t *testing.T) {
	sut := New(&fakeProducer{}, fakeProfiles{owner: uuid.Nil, exists: true}, &metrics.Metrics{}, false).
		WithRewind(&recordingRewindStore{}, noopRevokedPublisher{}, 5*time.Minute, time.Now)

	_, err := sut.Rewind(context.Background(), model.RewindRequest{}, userPrincipal())
	assertHTTPError(t, err, http.StatusForbidden)
}
