package metrics

import (
	"strconv"
	"sync/atomic"
)

type Metrics struct {
	Accepted        atomic.Uint64
	Rejected        atomic.Uint64
	QueueFull       atomic.Uint64
	Published       atomic.Uint64
	PublishFailed   atomic.Uint64
	AuthFailed      atomic.Uint64
	OwnershipDenied atomic.Uint64
	ConsumerFailed  atomic.Uint64
}

func (m *Metrics) AppendPrometheus(dst []byte, queueDepth, queueCapacity int) []byte {
	dst = appendCounter(dst, "swipes_accepted_total", m.Accepted.Load())
	dst = appendCounter(dst, "swipes_rejected_total", m.Rejected.Load())
	dst = appendCounter(dst, "swipes_queue_full_total", m.QueueFull.Load())
	dst = appendCounter(dst, "swipes_kafka_published_total", m.Published.Load())
	dst = appendCounter(dst, "swipes_kafka_publish_failed_total", m.PublishFailed.Load())
	dst = appendCounter(dst, "swipes_auth_failed_total", m.AuthFailed.Load())
	dst = appendCounter(dst, "swipes_ownership_denied_total", m.OwnershipDenied.Load())
	dst = appendCounter(dst, "swipes_consumer_failed_total", m.ConsumerFailed.Load())
	dst = appendGauge(dst, "swipes_queue_depth", queueDepth)
	return appendGauge(dst, "swipes_queue_capacity", queueCapacity)
}

func appendCounter(dst []byte, name string, value uint64) []byte {
	dst = append(dst, name...)
	dst = append(dst, ' ')
	dst = strconv.AppendUint(dst, value, 10)
	return append(dst, '\n')
}

func appendGauge(dst []byte, name string, value int) []byte {
	dst = append(dst, name...)
	dst = append(dst, ' ')
	dst = strconv.AppendInt(dst, int64(value), 10)
	return append(dst, '\n')
}
