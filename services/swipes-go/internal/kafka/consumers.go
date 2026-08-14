package kafka

import (
	"context"
	"encoding/json"
	"log"
	"sync"

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
	cancel  context.CancelFunc
	readers []*kafkago.Reader
	wg      sync.WaitGroup
}

func StartConsumers(parent context.Context, cfg config.Config, handler ProfileEventHandler, serviceMetrics *metrics.Metrics, logger *log.Logger) *Consumers {
	ctx, cancel := context.WithCancel(parent)
	consumers := &Consumers{cancel: cancel}
	if !cfg.ProfileConsumersEnabled {
		return consumers
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
	reader := kafkago.NewReader(kafkago.ReaderConfig{
		Brokers: cfg.KafkaBrokers, Topic: topic, GroupID: cfg.ProfileConsumerGroup + "-" + topic,
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
			if err := handle(ctx, message.Value); err != nil {
				serviceMetrics.ConsumerFailed.Add(1)
				logger.Printf("Kafka consumer rejected event topic=%s offset=%d: %v", topic, message.Offset, err)
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
}
