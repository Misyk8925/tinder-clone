package model

import (
	"bytes"
	"encoding/json"
	"fmt"
	"math"
	"strconv"
	"strings"
	"time"
)

const millisThreshold int64 = 1_000_000_000_000

// EventTimestamp accepts Jackson Instant numbers (epoch seconds with nanos, or
// epoch millis) and RFC3339 strings. Profiles' Kafka JsonSerializer writes the
// former; Go's time.Time only accepts the latter.
type EventTimestamp struct {
	time.Time
}

func (timestamp *EventTimestamp) UnmarshalJSON(data []byte) error {
	data = bytes.TrimSpace(data)
	if len(data) == 0 || bytes.Equal(data, []byte("null")) {
		return nil
	}
	switch data[0] {
	case '"':
		var raw string
		if err := json.Unmarshal(data, &raw); err != nil {
			return err
		}
		parsed, err := time.Parse(time.RFC3339Nano, raw)
		if err != nil {
			parsed, err = time.Parse(time.RFC3339, raw)
		}
		if err != nil {
			return fmt.Errorf("timestamp: %w", err)
		}
		timestamp.Time = parsed.UTC()
		return nil
	case '[':
		var parts []int64
		if err := json.Unmarshal(data, &parts); err != nil {
			return fmt.Errorf("timestamp: %w", err)
		}
		if len(parts) != 2 {
			return fmt.Errorf("timestamp: expected [seconds, nanos]")
		}
		timestamp.Time = time.Unix(parts[0], parts[1]).UTC()
		return nil
	default:
		var number json.Number
		if err := json.Unmarshal(data, &number); err != nil {
			return fmt.Errorf("timestamp: %w", err)
		}
		parsed, err := parseEpochNumber(number.String())
		if err != nil {
			return err
		}
		timestamp.Time = parsed
		return nil
	}
}

func parseEpochNumber(raw string) (time.Time, error) {
	if strings.ContainsAny(raw, "eE") {
		value, err := strconv.ParseFloat(raw, 64)
		if err != nil {
			return time.Time{}, fmt.Errorf("timestamp: %w", err)
		}
		seconds, fraction := math.Modf(value)
		return time.Unix(int64(seconds), int64(fraction*1e9)).UTC(), nil
	}
	dot := strings.IndexByte(raw, '.')
	if dot >= 0 {
		seconds, err := strconv.ParseInt(raw[:dot], 10, 64)
		if err != nil {
			return time.Time{}, fmt.Errorf("timestamp: %w", err)
		}
		fraction := raw[dot+1:]
		if len(fraction) > 9 {
			fraction = fraction[:9]
		}
		for len(fraction) < 9 {
			fraction += "0"
		}
		nanos, err := strconv.ParseInt(fraction, 10, 64)
		if err != nil {
			return time.Time{}, fmt.Errorf("timestamp: %w", err)
		}
		return time.Unix(seconds, nanos).UTC(), nil
	}
	value, err := strconv.ParseInt(raw, 10, 64)
	if err != nil {
		return time.Time{}, fmt.Errorf("timestamp: %w", err)
	}
	if value >= millisThreshold {
		return time.UnixMilli(value).UTC(), nil
	}
	return time.Unix(value, 0).UTC(), nil
}
