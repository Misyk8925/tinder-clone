package kafka

import (
	"context"
	"errors"
	"fmt"
	"log"
	"sync"
	"sync/atomic"
	"time"

	kafkago "github.com/segmentio/kafka-go"
	"tinder-clone/services/swipes-go/internal/config"
	"tinder-clone/services/swipes-go/internal/metrics"
	"tinder-clone/services/swipes-go/internal/model"
	"tinder-clone/services/swipes-go/internal/utils"
)

type Producer struct {
	writer  *kafkago.Writer
	queue   chan model.SwipeCommand
	metrics *metrics.Metrics
	log     *log.Logger
	wg      sync.WaitGroup
	sendMu  sync.RWMutex
	closed  chan struct{}
	once    sync.Once
	closing atomic.Bool
	batch   int
}

func NewProducer(ctx context.Context, cfg config.Config, serviceMetrics *metrics.Metrics, logger *log.Logger) (*Producer, error) {
	if cfg.ProducerWarmupEnabled {
		warmCtx, cancel := context.WithTimeout(ctx, 15*time.Second)
		defer cancel()
		conn, err := kafkago.DialLeader(warmCtx, "tcp", cfg.KafkaBrokers[0], cfg.SwipeTopic, 0)
		if err != nil {
			return nil, fmt.Errorf("Kafka readiness: %w", err)
		}
		_ = conn.Close()
	}
	writer := &kafkago.Writer{
		Addr: kafkago.TCP(cfg.KafkaBrokers...), Topic: cfg.SwipeTopic, Balancer: &kafkago.Hash{},
		BatchSize: cfg.ProducerBatchSize, BatchTimeout: cfg.ProducerBufferTimeout,
		RequiredAcks: kafkago.RequireOne, MaxAttempts: 5, AllowAutoTopicCreation: false,
		WriteTimeout: 10 * time.Second, ReadTimeout: 10 * time.Second,
		Completion: func(messages []kafkago.Message, err error) {
			if err != nil {
				serviceMetrics.PublishFailed.Add(uint64(len(messages)))
				logger.Printf("Kafka delivery failed count=%d: %v", len(messages), err)
				return
			}
			serviceMetrics.Published.Add(uint64(len(messages)))
		},
	}
	producer := &Producer{
		writer: writer, queue: make(chan model.SwipeCommand, cfg.ProducerQueueCapacity), metrics: serviceMetrics,
		log: logger, closed: make(chan struct{}), batch: cfg.ProducerBatchSize,
	}
	for i := 0; i < cfg.ProducerConcurrency; i++ {
		producer.wg.Add(1)
		go producer.worker()
	}
	return producer, nil
}

func (producer *Producer) Send(ctx context.Context, command model.SwipeCommand) error {
	producer.sendMu.RLock()
	defer producer.sendMu.RUnlock()
	if producer.closing.Load() {
		return errors.New("swipe producer is closed")
	}
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-producer.closed:
		return errors.New("swipe producer is closed")
	case producer.queue <- command:
		return nil
	default:
		return model.ErrQueueFull
	}
}

func (producer *Producer) QueueStats() (int, int) { return len(producer.queue), cap(producer.queue) }

func (producer *Producer) Close() error {
	producer.once.Do(func() {
		producer.sendMu.Lock()
		producer.closing.Store(true)
		close(producer.closed)
		producer.sendMu.Unlock()
		producer.wg.Wait()
	})
	return producer.writer.Close()
}

func (producer *Producer) worker() {
	defer producer.wg.Done()
	batch := make([]model.SwipeCommand, 0, producer.batch)
	for {
		var command model.SwipeCommand
		select {
		case command = <-producer.queue:
		case <-producer.closed:
			for {
				select {
				case command = <-producer.queue:
					batch = append(batch[:0], command)
					producer.publish(batch)
				default:
					return
				}
			}
		}
		batch = append(batch[:0], command)
		for len(batch) < cap(batch) {
			select {
			case next := <-producer.queue:
				batch = append(batch, next)
			default:
				producer.publish(batch)
				goto nextBatch
			}
		}
		producer.publish(batch)
	nextBatch:
	}
}

func (producer *Producer) publish(batch []model.SwipeCommand) {
	messages := buildMessages(batch)
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := producer.writer.WriteMessages(ctx, messages...); err != nil {
		producer.log.Printf("Kafka batch write failed count=%d: %v", len(messages), err)
	}
}

func buildMessages(batch []model.SwipeCommand) []kafkago.Message {
	messages := make([]kafkago.Message, 0, len(batch))
	values := make([]byte, 0, len(batch)*192)
	keys := make([]byte, 0, len(batch)*36)
	for _, command := range batch {
		event := model.NewSwipeCreatedEvent(command)
		valueStart := len(values)
		values = utils.EncodeEvent(event, values)
		keyStart := len(keys)
		keys = append(keys, event.Profile1ID...)
		messages = append(messages, kafkago.Message{
			Key: keys[keyStart:len(keys)], Value: values[valueStart:len(values)], Time: time.UnixMilli(event.Timestamp),
		})
	}
	return messages
}
