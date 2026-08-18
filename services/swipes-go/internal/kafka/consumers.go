package kafka

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"sync"
	"time"

	kafkago "github.com/segmentio/kafka-go"
	"tinder-clone/services/swipes-go/internal/config"
	"tinder-clone/services/swipes-go/internal/metrics"
	"tinder-clone/services/swipes-go/internal/model"
)

type ProfileEventHandler interface {
	SaveProfile(context.Context, model.ProfileCreateEvent) error
	DeleteProfile(context.Context, model.ProfileDeleteEvent) error
}

type Consumers struct {
	cancel           context.CancelFunc
	readers          []*kafkago.Reader
	deadLetterWriter messageWriter
	wg               sync.WaitGroup
}

func StartConsumers(parent context.Context, cfg config.Config, handler ProfileEventHandler, serviceMetrics *metrics.Metrics, logger *log.Logger) *Consumers {
	ctx, cancel := context.WithCancel(parent)
	consumers := &Consumers{cancel: cancel}
	if !cfg.ProfileConsumersEnabled {
		return consumers
	}
	consumers.deadLetterWriter = &kafkago.Writer{
		Addr:                   kafkago.TCP(cfg.KafkaBrokers...),
		Balancer:               &kafkago.Hash{},
		RequiredAcks:           kafkago.RequireAll,
		MaxAttempts:            5,
		AllowAutoTopicCreation: false,
		WriteTimeout:           10 * time.Second,
		ReadTimeout:            10 * time.Second,
	}
	consumers.start(ctx, cfg, cfg.ProfileCreatedTopic, func(ctx context.Context, value []byte) error {
		var event model.ProfileCreateEvent
		if err := json.Unmarshal(value, &event); err != nil {
			return err
		}
		return handler.SaveProfile(ctx, event)
	}, serviceMetrics, logger)
	consumers.start(ctx, cfg, cfg.ProfileDeletedTopic, func(ctx context.Context, value []byte) error {
		var event model.ProfileDeleteEvent
		if err := json.Unmarshal(value, &event); err != nil {
			return err
		}
		return handler.DeleteProfile(ctx, event)
	}, serviceMetrics, logger)
	return consumers
}

func (consumers *Consumers) start(ctx context.Context, cfg config.Config, topic string, handle func(context.Context, []byte) error, serviceMetrics *metrics.Metrics, logger *log.Logger) {
	consumerGroup := cfg.ProfileConsumerGroup + "-" + topic
	reader := kafkago.NewReader(kafkago.ReaderConfig{
		Brokers: cfg.KafkaBrokers, Topic: topic, GroupID: consumerGroup,
		StartOffset: kafkago.FirstOffset, MinBytes: 1, MaxBytes: 10e6,
	})
	consumers.readers = append(consumers.readers, reader)
	consumers.wg.Add(1)
	go func() {
		defer consumers.wg.Done()
		for {
			message, err := reader.FetchMessage(ctx)
			if err != nil {
				if ctx.Err() != nil {
					return
				}
				serviceMetrics.ConsumerFailed.Add(1)
				logger.Printf("Kafka consumer fetch failed topic=%s: %v", topic, err)
				continue
			}
			if err := processMessage(
				ctx,
				topic,
				consumerGroup,
				message,
				handle,
				consumers.deadLetterWriter,
				cfg.ConsumerMaxRetries,
				cfg.ConsumerRetryBackoff,
				serviceMetrics,
				logger,
			); err != nil {
				if ctx.Err() != nil {
					return
				}
				serviceMetrics.ConsumerFailed.Add(1)
				logger.Printf("Kafka consumer could not recover event topic=%s offset=%d: %v", topic, message.Offset, err)
				continue
			}
			if err := reader.CommitMessages(ctx, message); err != nil && ctx.Err() == nil {
				serviceMetrics.ConsumerFailed.Add(1)
				logger.Printf("Kafka consumer commit failed topic=%s offset=%d: %v", topic, message.Offset, err)
			}
		}
	}()
}

func (consumers *Consumers) Close() {
	consumers.cancel()
	for _, reader := range consumers.readers {
		_ = reader.Close()
	}
	consumers.wg.Wait()
	if consumers.deadLetterWriter != nil {
		_ = consumers.deadLetterWriter.Close()
	}
}

func processMessage(
	ctx context.Context,
	topic string,
	consumerGroup string,
	message kafkago.Message,
	handle func(context.Context, []byte) error,
	deadLetterWriter messageWriter,
	maxRetries int,
	retryBackoff time.Duration,
	serviceMetrics *metrics.Metrics,
	logger *log.Logger,
) error {
	var handleErr error
	for attempt := 0; attempt <= maxRetries; attempt++ {
		handleErr = handle(ctx, message.Value)
		if handleErr == nil {
			return nil
		}
		serviceMetrics.ConsumerFailed.Add(1)
		logger.Printf(
			"Kafka consumer rejected event topic=%s offset=%d attempt=%d/%d: %v",
			topic,
			message.Offset,
			attempt+1,
			maxRetries+1,
			handleErr,
		)
		if attempt < maxRetries && !waitForRetry(ctx, retryBackoff) {
			return ctx.Err()
		}
	}

	deadLetter := kafkago.Message{
		Topic: topic + ".dlt",
		Key:   message.Key,
		Value: message.Value,
		Time:  time.Now(),
		Headers: append(
			append([]kafkago.Header{}, message.Headers...),
			kafkago.Header{Key: "x-original-topic", Value: []byte(topic)},
			kafkago.Header{Key: "x-consumer-group", Value: []byte(consumerGroup)},
			kafkago.Header{Key: "x-original-partition", Value: []byte(fmt.Sprint(message.Partition))},
			kafkago.Header{Key: "x-original-offset", Value: []byte(fmt.Sprint(message.Offset))},
			kafkago.Header{Key: "x-exception-message", Value: []byte(handleErr.Error())},
		),
	}

	for {
		if err := deadLetterWriter.WriteMessages(ctx, deadLetter); err == nil {
			return nil
		} else {
			serviceMetrics.ConsumerFailed.Add(1)
			logger.Printf("Kafka DLT publish failed topic=%s offset=%d: %v", topic, message.Offset, err)
		}
		if !waitForRetry(ctx, retryBackoff) {
			return ctx.Err()
		}
	}
}

func waitForRetry(ctx context.Context, backoff time.Duration) bool {
	if backoff <= 0 {
		return ctx.Err() == nil
	}
	timer := time.NewTimer(backoff)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-timer.C:
		return true
	}
}
