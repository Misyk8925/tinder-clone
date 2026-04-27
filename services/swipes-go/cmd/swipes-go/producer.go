package main

import (
	"context"
	"errors"
	"log"
	"sync"
	"time"

	"github.com/segmentio/kafka-go"
)

var errProducerQueueFull = errors.New("swipe producer queue is full")

type SwipeEventProducer interface {
	Send(context.Context, SwipeCommand) error
}

type SwipeProducer struct {
	cfg    Config
	writer *kafka.Writer
	queue  chan SwipeCommand
	log    *log.Logger
	wg     sync.WaitGroup
	closed chan struct{}
}

func NewSwipeProducer(ctx context.Context, cfg Config, logger *log.Logger) (*SwipeProducer, error) {
	writer := &kafka.Writer{
		Addr:                   kafka.TCP(cfg.KafkaBrokers...),
		Topic:                  cfg.SwipeTopic,
		Balancer:               &kafka.Hash{},
		BatchSize:              cfg.ProducerBatchSize,
		BatchTimeout:           cfg.ProducerBufferTimeout,
		RequiredAcks:           kafka.RequireOne,
		AllowAutoTopicCreation: false,
		Async:                  false,
		WriteTimeout:           10 * time.Second,
		ReadTimeout:            10 * time.Second,
	}
	producer := &SwipeProducer{
		cfg:    cfg,
		writer: writer,
		queue:  make(chan SwipeCommand, cfg.ProducerQueueCapacity),
		log:    logger,
		closed: make(chan struct{}),
	}
	if cfg.ProducerWarmupEnabled {
		producer.warm(ctx)
	}
	for i := 0; i < cfg.ProducerConcurrency; i++ {
		producer.wg.Add(1)
		go producer.worker(i)
	}
	logger.Printf("started swipe producer: brokers=%v topic=%s concurrency=%d batchSize=%d timeout=%s capacity=%d",
		cfg.KafkaBrokers, cfg.SwipeTopic, cfg.ProducerConcurrency, cfg.ProducerBatchSize,
		cfg.ProducerBufferTimeout, cfg.ProducerQueueCapacity)
	return producer, nil
}

func (producer *SwipeProducer) Send(ctx context.Context, command SwipeCommand) error {
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-producer.closed:
		return errors.New("swipe producer is closed")
	case producer.queue <- command:
		return nil
	default:
		return errProducerQueueFull
	}
}

func (producer *SwipeProducer) Close() error {
	close(producer.closed)
	close(producer.queue)
	producer.wg.Wait()
	return producer.writer.Close()
}

func (producer *SwipeProducer) warm(parent context.Context) {
	ctx, cancel := context.WithTimeout(parent, 15*time.Second)
	defer cancel()
	conn, err := kafka.DialContext(ctx, "tcp", producer.cfg.KafkaBrokers[0])
	if err != nil {
		producer.log.Printf("kafka producer warmup failed: %v", err)
		return
	}
	defer conn.Close()
	partitions, err := conn.ReadPartitions(producer.cfg.SwipeTopic)
	if err != nil {
		producer.log.Printf("kafka producer warmup failed: %v", err)
		return
	}
	producer.log.Printf("warmed kafka producer for topic %s with %d partitions", producer.cfg.SwipeTopic, len(partitions))
}

func (producer *SwipeProducer) worker(id int) {
	defer producer.wg.Done()
	batch := make([]SwipeCommand, 0, producer.cfg.ProducerBatchSize)
	for {
		command, ok := <-producer.queue
		if !ok {
			return
		}
		batch = append(batch[:0], command)
		for len(batch) < producer.cfg.ProducerBatchSize {
			select {
			case next, ok := <-producer.queue:
				if !ok {
					goto publishAndReturn
				}
				batch = append(batch, next)
			default:
				goto publish
			}
		}
	publish:
		producer.publishBatch(id, batch)
		continue
	publishAndReturn:
		producer.publishBatch(id, batch)
		return
	}
}

func (producer *SwipeProducer) publishBatch(workerID int, batch []SwipeCommand) {
	messages := make([]kafka.Message, len(batch))
	for i, command := range batch {
		event := NewSwipeCreatedEventFromCommand(command)
		messages[i] = kafka.Message{
			Key:   []byte(event.Profile1ID),
			Value: event.JSON(),
			Time:  time.UnixMilli(event.Timestamp),
		}
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := producer.writer.WriteMessages(ctx, messages...); err != nil {
		producer.log.Printf("failed to send swipe event batch worker=%d size=%d: %v", workerID, len(batch), err)
	}
}
