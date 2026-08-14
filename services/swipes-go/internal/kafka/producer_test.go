package kafka

import (
	"encoding/json"
	"testing"

	"tinder-clone/services/swipes-go/internal/model"
)

func TestBuildMessagesKeepsKeysAndValuesIndependent(t *testing.T) {
	commands := []model.SwipeCommand{
		{Profile1ID: "first", Profile2ID: "target-a", Decision: true},
		{Profile1ID: "second", Profile2ID: "target-b", IsSuper: true},
	}
	messages := buildMessages(commands)
	for i, message := range messages {
		if string(message.Key) != commands[i].Profile1ID {
			t.Fatalf("message %d key=%q", i, message.Key)
		}
		var event map[string]any
		if err := json.Unmarshal(message.Value, &event); err != nil {
			t.Fatalf("message %d JSON: %v", i, err)
		}
		if event["profile1Id"] != commands[i].Profile1ID {
			t.Fatalf("message %d event=%s", i, message.Value)
		}
	}
}

func BenchmarkBuildMessages(b *testing.B) {
	commands := make([]model.SwipeCommand, 500)
	for i := range commands {
		commands[i] = model.SwipeCommand{Profile1ID: "249bea58-449e-4bb6-9243-8f16efec14e0", Profile2ID: "44799e38-8299-4697-a8a1-2c56ccededfd", Decision: true}
	}
	b.ReportAllocs()
	for i := 0; i < b.N; i++ {
		_ = buildMessages(commands)
	}
}
