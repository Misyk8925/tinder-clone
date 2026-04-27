package main

import (
	"context"
	"net/http"
	"testing"

	"github.com/google/uuid"
)

type fakeProducer struct {
	commands []SwipeCommand
	err      error
}

func (producer *fakeProducer) Send(_ context.Context, command SwipeCommand) error {
	if producer.err != nil {
		return producer.err
	}
	producer.commands = append(producer.commands, command)
	return nil
}

type fakeProfileCache struct {
	exists bool
	calls  int
}

func (cache *fakeProfileCache) ExistsAll(_ context.Context, _, _ uuid.UUID, _ string) (bool, error) {
	cache.calls++
	return cache.exists, nil
}

func TestSendSwipeRejectsSuperWithoutPremium(t *testing.T) {
	isSuper := true
	service := NewSwipeService(&fakeProducer{}, &fakeProfileCache{exists: true}, false)
	err := service.SendSwipe(context.Background(), SwipeDTO{
		Profile1ID: uuid.NewString(),
		Profile2ID: uuid.NewString(),
		IsSuper:    &isSuper,
	}, false, "token", false)
	assertHTTPError(t, err, http.StatusForbidden, "Super like requires a premium or admin account")
}

func TestSendSwipePublishesWhenProfilesExist(t *testing.T) {
	producer := &fakeProducer{}
	cache := &fakeProfileCache{exists: true}
	service := NewSwipeService(producer, cache, false)
	dto := SwipeDTO{
		Profile1ID: uuid.NewString(),
		Profile2ID: uuid.NewString(),
		Decision:   true,
	}
	if err := service.SendSwipe(context.Background(), dto, false, "token", false); err != nil {
		t.Fatalf("SendSwipe returned error: %v", err)
	}
	if cache.calls != 1 {
		t.Fatalf("expected profile cache call, got %d", cache.calls)
	}
	if len(producer.commands) != 1 {
		t.Fatalf("expected one command, got %d", len(producer.commands))
	}
	if producer.commands[0].Profile1ID != dto.Profile1ID || !producer.commands[0].Decision {
		t.Fatalf("unexpected command: %+v", producer.commands[0])
	}
}

func TestSendSwipeTrustedBypassSkipsProfileLookup(t *testing.T) {
	producer := &fakeProducer{}
	cache := &fakeProfileCache{exists: false}
	service := NewSwipeService(producer, cache, true)
	dto := SwipeDTO{
		Profile1ID: "not-a-uuid",
		Profile2ID: "also-not-a-uuid",
	}
	if err := service.SendSwipe(context.Background(), dto, false, "", true); err != nil {
		t.Fatalf("SendSwipe returned error: %v", err)
	}
	if cache.calls != 0 {
		t.Fatalf("expected no profile cache calls, got %d", cache.calls)
	}
	if len(producer.commands) != 1 {
		t.Fatalf("expected one command, got %d", len(producer.commands))
	}
}

func TestSendSwipeQueueFullMapsTo429(t *testing.T) {
	service := NewSwipeService(&fakeProducer{err: errProducerQueueFull}, &fakeProfileCache{exists: true}, true)
	err := service.SendTrustedInternalSwipe(context.Background(), TrustedSwipe{
		Profile1ID: "a",
		Profile2ID: "b",
	}, false)
	assertHTTPError(t, err, http.StatusTooManyRequests, "Swipe producer queue is full")
}
