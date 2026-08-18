package utils

import (
	"encoding/json"
	"testing"

	"github.com/google/uuid"
	"tinder-clone/services/swipes-go/internal/model"
)

var benchmarkSwipe = []byte(`{"profile1Id":"249bea58-449e-4bb6-9243-8f16efec14e0","profile2Id":"44799e38-8299-4697-a8a1-2c56ccededfd","decision":true,"isSuper":false}`)

func TestEncodeEventProducesValidContractJSON(t *testing.T) {
	event := model.NewSwipeCreatedEvent(model.SwipeCommand{Profile1ID: "a", Profile2ID: "b", Decision: true})
	raw := EncodeEvent(event, nil)
	var decoded map[string]any
	if err := json.Unmarshal(raw, &decoded); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	for _, field := range []string{"eventId", "profile1Id", "profile2Id", "decision", "isSuper", "timestamp"} {
		if _, ok := decoded[field]; !ok {
			t.Fatalf("missing %s in %s", field, raw)
		}
	}
	if _, err := uuid.Parse(decoded["eventId"].(string)); err != nil {
		t.Fatalf("eventId is not a UUID: %v", err)
	}
}

func BenchmarkDecodeSwipe(b *testing.B) {
	b.ReportAllocs()
	for i := 0; i < b.N; i++ {
		if _, err := DecodeSwipe(benchmarkSwipe); err != nil {
			b.Fatal(err)
		}
	}
}

func BenchmarkEncodeEvent(b *testing.B) {
	event := model.SwipeCreatedEvent{Profile1ID: "a", Profile2ID: "b", Timestamp: 1}
	b.ReportAllocs()
	for i := 0; i < b.N; i++ {
		buf := make([]byte, 0, 192)
		_ = EncodeEvent(event, buf)
	}
}
