package kafka

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log"
	"testing"
	"time"

	kafkago "github.com/segmentio/kafka-go"
	"tinder-clone/services/swipes-go/internal/metrics"
	"tinder-clone/services/swipes-go/internal/model"
)

type blockingWriter struct {
	started chan struct{}
	release chan struct{}
	err     error
}

func (writer *blockingWriter) WriteMessages(context.Context, ...kafkago.Message) error {
	close(writer.started)
	<-writer.release
	return writer.err
}

func (writer *blockingWriter) Close() error { return nil }

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

func TestSendWaitsForBrokerAcknowledgementAndReturnsDeliveryFailure(t *testing.T) {
	deliveryErr := errors.New("broker unavailable")
	writer := &blockingWriter{started: make(chan struct{}), release: make(chan struct{}), err: deliveryErr}
	producer := &Producer{
		writer: writer, queue: make(chan pendingSwipe, 10), metrics: &metrics.Metrics{},
		log: log.New(io.Discard, "", 0), closed: make(chan struct{}), batch: 10,
	}
	producer.wg.Add(1)
	go producer.worker()
	defer producer.Close()

	result := make(chan error, 1)
	go func() {
		result <- producer.Send(context.Background(), model.SwipeCommand{
			Profile1ID: "first", Profile2ID: "second", Decision: true,
		})
	}()

	select {
	case <-writer.started:
	case <-time.After(time.Second):
		t.Fatal("writer did not receive the swipe")
	}
	select {
	case err := <-result:
		t.Fatalf("send completed before broker result: %v", err)
	case <-time.After(25 * time.Millisecond):
	}

	close(writer.release)
	select {
	case err := <-result:
		if !errors.Is(err, deliveryErr) {
			t.Fatalf("send error=%v, want %v", err, deliveryErr)
		}
	case <-time.After(time.Second):
		t.Fatal("send did not return the broker failure")
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
