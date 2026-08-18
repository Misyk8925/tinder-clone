package kafka

import (
	"context"
	"errors"
	"io"
	"log"
	"testing"

	kafkago "github.com/segmentio/kafka-go"
	"tinder-clone/services/swipes-go/internal/metrics"
)

type recordingWriter struct {
	failures int
	writes   []kafkago.Message
}

func (writer *recordingWriter) WriteMessages(_ context.Context, messages ...kafkago.Message) error {
	if writer.failures > 0 {
		writer.failures--
		return errors.New("dlt unavailable")
	}
	writer.writes = append(writer.writes, messages...)
	return nil
}

func (writer *recordingWriter) Close() error { return nil }

func TestProcessMessageRetriesThenPublishesToDurableDLT(t *testing.T) {
	writer := &recordingWriter{failures: 1}
	attempts := 0
	message := kafkago.Message{
		Topic: "profile.deleted", Partition: 3, Offset: 42,
		Key: []byte("profile-1"), Value: []byte(`{"profileId":"profile-1"}`),
	}

	err := processMessage(
		context.Background(),
		message.Topic,
		"swipes-profile-cache-profile.deleted",
		message,
		func(context.Context, []byte) error {
			attempts++
			return errors.New("redis unavailable")
		},
		writer,
		2,
		0,
		&metrics.Metrics{},
		log.New(io.Discard, "", 0),
	)

	if err != nil {
		t.Fatalf("expected recovery to complete: %v", err)
	}
	if attempts != 3 {
		t.Fatalf("expected initial attempt plus two retries, got %d", attempts)
	}
	if len(writer.writes) != 1 {
		t.Fatalf("expected one DLT record, got %d", len(writer.writes))
	}
	if writer.writes[0].Topic != "profile.deleted.dlt" {
		t.Fatalf("unexpected DLT topic: %s", writer.writes[0].Topic)
	}
	if string(writer.writes[0].Key) != "profile-1" {
		t.Fatalf("expected original record key to be preserved")
	}
	if !hasHeader(writer.writes[0], "x-consumer-group", "swipes-profile-cache-profile.deleted") {
		t.Fatalf("expected DLT record to identify the failed consumer group")
	}
}

func TestProcessMessageDoesNotPublishSuccessfulRecordsToDLT(t *testing.T) {
	writer := &recordingWriter{}

	err := processMessage(
		context.Background(),
		"profile.created",
		"swipes-profile-cache-profile.created",
		kafkago.Message{Value: []byte(`{}`)},
		func(context.Context, []byte) error { return nil },
		writer,
		5,
		0,
		&metrics.Metrics{},
		log.New(io.Discard, "", 0),
	)

	if err != nil {
		t.Fatalf("expected successful processing: %v", err)
	}
	if len(writer.writes) != 0 {
		t.Fatalf("expected no DLT writes, got %d", len(writer.writes))
	}
}

func hasHeader(message kafkago.Message, key, value string) bool {
	for _, header := range message.Headers {
		if header.Key == key && string(header.Value) == value {
			return true
		}
	}
	return false
}
