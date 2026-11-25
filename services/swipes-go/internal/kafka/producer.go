package kafka

import (
	"context"
	"encoding/json"
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
	writer        messageWriter
	revokedWriter messageWriter
	queue         chan pendingSwipe
	metrics       *metrics.Metrics
	log           *log.Logger
	wg            sync.WaitGroup
	sendMu        sync.RWMutex
	closed        chan struct{}
	once          sync.Once
	closing       atomic.Bool
	batch         int
}

type messageWriter interface {
	WriteMessages(context.Context, ...kafkago.Message) error
	Close() error
}

type pendingSwipe struct {
	command model.SwipeCommand
	result  chan error
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
		RequiredAcks: kafkago.RequireAll, MaxAttempts: 5, AllowAutoTopicCreation: false,
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
	revokedTopic := cfg.SwipeRevokedTopic
	if revokedTopic == "" {
		revokedTopic = "swipe-revoked"
	}
	revokedWriter := &kafkago.Writer{
		Addr: kafkago.TCP(cfg.KafkaBrokers...), Topic: revokedTopic, Balancer: &kafkago.Hash{},
		BatchSize: 1, BatchTimeout: time.Millisecond,
		RequiredAcks: kafkago.RequireAll, MaxAttempts: 5, AllowAutoTopicCreation: false,
		WriteTimeout: 5 * time.Second, ReadTimeout: 5 * time.Second,
	}
	producer := &Producer{
		writer: writer, revokedWriter: revokedWriter,
		queue: make(chan pendingSwipe, cfg.ProducerQueueCapacity), metrics: serviceMetrics,
		log: logger, closed: make(chan struct{}), batch: cfg.ProducerBatchSize,
	}
	for i := 0; i < cfg.ProducerConcurrency; i++ {
		producer.wg.Add(1)
		go producer.worker()
	}
	return producer, nil
}

func (producer *Producer) Send(ctx context.Context, command model.SwipeCommand) error {
	pending := pendingSwipe{command: command, result: make(chan error, 1)}
	producer.sendMu.RLock()
	if producer.closing.Load() {
		producer.sendMu.RUnlock()
		return errors.New("swipe producer is closed")
	}
	select {
	case <-ctx.Done():
		producer.sendMu.RUnlock()
		return ctx.Err()
	case <-producer.closed:
		producer.sendMu.RUnlock()
		return errors.New("swipe producer is closed")
	case producer.queue <- pending:
		producer.sendMu.RUnlock()
	default:
		producer.sendMu.RUnlock()
		return model.ErrQueueFull
	}

	select {
	case <-ctx.Done():
		return ctx.Err()
	case err := <-pending.result:
		return err
	}
}

func (producer *Producer) PublishRevoked(ctx context.Context, event model.SwipeRevokedEvent) error {
	if producer.revokedWriter == nil {
		return errors.New("swipe revoked producer is not configured")
	}
	payload, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("marshal swipe revoked event: %w", err)
	}
	msg := kafkago.Message{
		Key:   []byte(event.Profile1ID),
		Value: payload,
		Time:  time.UnixMilli(event.RevokedAt),
	}
	if err := producer.revokedWriter.WriteMessages(ctx, msg); err != nil {
		if producer.metrics != nil {
			producer.metrics.PublishFailed.Add(1)
		}
		return err
	}
	if producer.metrics != nil {
		producer.metrics.Published.Add(1)
	}
	return nil
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
	err1 := producer.writer.Close()
	var err2 error
	if producer.revokedWriter != nil {
		err2 = producer.revokedWriter.Close()
	}
	return errors.Join(err1, err2)
}

func (producer *Producer) worker() {
	defer producer.wg.Done()
	batch := make([]pendingSwipe, 0, producer.batch)
	for {
		var pending pendingSwipe
		select {
		case pending = <-producer.queue:
		case <-producer.closed:
			for {
				select {
				case pending = <-producer.queue:
					batch = append(batch[:0], pending)
					producer.publish(batch)
				default:
					return
				}
			}
		}
		batch = append(batch[:0], pending)
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

func (producer *Producer) publish(batch []pendingSwipe) {
	commands := make([]model.SwipeCommand, len(batch))
	for i := range batch {
		commands[i] = batch[i].command
	}
	messages := buildMessages(commands)
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	err := producer.writer.WriteMessages(ctx, messages...)
	if err != nil {
		producer.log.Printf("Kafka batch write failed count=%d: %v", len(messages), err)
	}
	for _, pending := range batch {
		pending.result <- err
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
