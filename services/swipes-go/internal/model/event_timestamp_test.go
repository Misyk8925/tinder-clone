package model

import (
	"encoding/json"
	"testing"
	"time"
)

func TestGivenJacksonEpochSecondsWhenProfileCreatedIsUnmarshalledThenTimestampIsParsed(t *testing.T) {
	// Live profile.created payload from local Kafka offset 292 (Jackson Instant number).
	payload := `{"eventId":"be0062e6-2adf-4777-978d-a24c664505c5","profileId":"7e7dac54-26c8-4e51-ab68-2708ea4a278b","userId":"972bb558-f7f4-4404-8ffc-368162014e18","timestamp":1787342115.082518133}`

	var event ProfileCreateEvent
	if err := json.Unmarshal([]byte(payload), &event); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if event.Timestamp == nil {
		t.Fatal("expected timestamp to be set")
	}
	got := event.Timestamp.UTC()
	want := time.Unix(1787342115, 82518133).UTC()
	if !got.Equal(want) {
		t.Fatalf("timestamp=%s want=%s", got.Format(time.RFC3339Nano), want.Format(time.RFC3339Nano))
	}
	if event.ProfileID != "7e7dac54-26c8-4e51-ab68-2708ea4a278b" {
		t.Fatalf("profileId=%s", event.ProfileID)
	}
	if event.UserID == nil || *event.UserID != "972bb558-f7f4-4404-8ffc-368162014e18" {
		t.Fatalf("userId=%v", event.UserID)
	}
}

func TestGivenRfc3339StringWhenProfileCreatedIsUnmarshalledThenTimestampIsParsed(t *testing.T) {
	payload := `{"eventId":"e1","profileId":"p1","userId":"u1","timestamp":"2026-08-22T12:22:47.082Z"}`

	var event ProfileCreateEvent
	if err := json.Unmarshal([]byte(payload), &event); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	want := time.Date(2026, 8, 22, 12, 22, 47, 82_000_000, time.UTC)
	if event.Timestamp == nil || !event.Timestamp.UTC().Equal(want) {
		t.Fatalf("timestamp=%v want=%s", event.Timestamp, want)
	}
}

func TestGivenEpochMillisWhenProfileDeletedIsUnmarshalledThenTimestampIsParsed(t *testing.T) {
	payload := `{"eventId":"e1","profileId":"p1","timestamp":1787342115082}`

	var event ProfileDeleteEvent
	if err := json.Unmarshal([]byte(payload), &event); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	want := time.UnixMilli(1787342115082).UTC()
	if event.Timestamp == nil || !event.Timestamp.UTC().Equal(want) {
		t.Fatalf("timestamp=%v want=%s", event.Timestamp, want)
	}
}

func TestGivenNullTimestampWhenProfileCreatedIsUnmarshalledThenEventStillSucceeds(t *testing.T) {
	payload := `{"eventId":"e1","profileId":"p1","userId":"u1","timestamp":null}`

	var event ProfileCreateEvent
	if err := json.Unmarshal([]byte(payload), &event); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if event.Timestamp != nil {
		t.Fatalf("expected nil timestamp, got %v", event.Timestamp)
	}
}
