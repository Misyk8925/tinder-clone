package service

import (
	"context"
	"errors"
	"net/http"
	"testing"

	"github.com/google/uuid"
	"tinder-clone/services/swipes-go/internal/metrics"
	"tinder-clone/services/swipes-go/internal/model"
	"tinder-clone/services/swipes-go/internal/security"
)

type fakeProducer struct {
	err      error
	commands []model.SwipeCommand
}

func (producer *fakeProducer) Send(_ context.Context, command model.SwipeCommand) error {
	if producer.err == nil {
		producer.commands = append(producer.commands, command)
	}
	return producer.err
}

type fakeProfiles struct {
	owner  uuid.UUID
	exists bool
	err    error
}

func (profiles fakeProfiles) ExistsAll(context.Context, uuid.UUID, uuid.UUID, string) (bool, error) {
	return profiles.exists, profiles.err
}

func (profiles fakeProfiles) ProfileIDForUser(context.Context, string, string) (uuid.UUID, error) {
	return profiles.owner, profiles.err
}

func TestSendRejectsProfileImpersonation(t *testing.T) {
	first, second := uuid.New(), uuid.New()
	serviceMetrics := &metrics.Metrics{}
	sut := New(&fakeProducer{}, fakeProfiles{owner: uuid.New(), exists: true}, serviceMetrics, false)
	err := sut.Send(context.Background(), swipe(first, second, false), userPrincipal(), false)
	assertHTTPError(t, err, http.StatusForbidden)
	if serviceMetrics.OwnershipDenied.Load() != 1 {
		t.Fatal("expected ownership denial metric")
	}
}

func TestSendRejectsSuperRouteWithoutRole(t *testing.T) {
	first, second := uuid.New(), uuid.New()
	sut := New(&fakeProducer{}, fakeProfiles{owner: first, exists: true}, &metrics.Metrics{}, false)
	err := sut.Send(context.Background(), swipe(first, second, true), userPrincipal(), true)
	assertHTTPError(t, err, http.StatusForbidden)
}

func TestSendAllowsPremiumOwner(t *testing.T) {
	first, second := uuid.New(), uuid.New()
	producer := &fakeProducer{}
	sut := New(producer, fakeProfiles{owner: first, exists: true}, &metrics.Metrics{}, false)
	principal := userPrincipal()
	principal.Roles["USER_PREMIUM"] = struct{}{}
	if err := sut.Send(context.Background(), swipe(first, second, true), principal, true); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(producer.commands) != 1 || !producer.commands[0].IsSuper {
		t.Fatalf("unexpected commands: %+v", producer.commands)
	}
}

func TestSendMapsFullQueueTo429(t *testing.T) {
	first, second := uuid.New(), uuid.New()
	sut := New(&fakeProducer{err: model.ErrQueueFull}, fakeProfiles{owner: first, exists: true}, &metrics.Metrics{}, false)
	err := sut.Send(context.Background(), swipe(first, second, false), userPrincipal(), false)
	assertHTTPError(t, err, http.StatusTooManyRequests)
}

func TestBenchmarkBypassSkipsUUIDAndProfileChecks(t *testing.T) {
	producer := &fakeProducer{}
	sut := New(producer, fakeProfiles{err: errors.New("must not be called")}, &metrics.Metrics{}, true)
	dto := model.SwipeDTO{Profile1ID: "benchmark-a", Profile2ID: "benchmark-b"}
	if err := sut.Send(context.Background(), dto, security.Principal{Benchmark: true}, false); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

func swipe(first, second uuid.UUID, super bool) model.SwipeDTO {
	return model.SwipeDTO{Profile1ID: first.String(), Profile2ID: second.String(), IsSuper: &super}
}

func userPrincipal() security.Principal {
	return security.Principal{Subject: "user-1", Bearer: "token", Roles: map[string]struct{}{}}
}

func assertHTTPError(t *testing.T, err error, status int) {
	t.Helper()
	var httpErr model.HTTPError
	if !errors.As(err, &httpErr) || httpErr.Status != status {
		t.Fatalf("expected HTTP %d, got %v", status, err)
	}
}
