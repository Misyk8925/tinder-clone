package main

import (
	"strconv"
	"sync/atomic"
	"time"
)

type SwipeDTO struct {
	Profile1ID string `json:"profile1Id"`
	Profile2ID string `json:"profile2Id"`
	Decision   bool   `json:"decision"`
	IsSuper    *bool  `json:"isSuper"`
}

func (dto SwipeDTO) super() bool {
	return dto.IsSuper != nil && *dto.IsSuper
}

type TrustedSwipe struct {
	Profile1ID string
	Profile2ID string
	Decision   bool
	IsSuper    bool
}

type SwipeCommand struct {
	Profile1ID string
	Profile2ID string
	Decision   bool
	IsSuper    bool
}

type SwipeCreatedEvent struct {
	EventID    string
	Profile1ID string
	Profile2ID string
	Decision   bool
	IsSuper    bool
	Timestamp  int64
}

var eventSequence atomic.Uint64

func init() {
	eventSequence.Store(uint64(time.Now().UnixNano()))
}

func NewSwipeCreatedEvent(profile1ID, profile2ID string, decision, isSuper bool) SwipeCreatedEvent {
	nowMillis := time.Now().UnixMilli()
	seq := eventSequence.Add(1)
	return SwipeCreatedEvent{
		EventID:    uuidFromParts(uint64(nowMillis), seq),
		Profile1ID: profile1ID,
		Profile2ID: profile2ID,
		Decision:   decision,
		IsSuper:    isSuper,
		Timestamp:  nowMillis,
	}
}

func NewSwipeCreatedEventFromCommand(command SwipeCommand) SwipeCreatedEvent {
	return NewSwipeCreatedEvent(command.Profile1ID, command.Profile2ID, command.Decision, command.IsSuper)
}

func (event SwipeCreatedEvent) JSON() []byte {
	buf := make([]byte, 0, len(event.Profile1ID)+len(event.Profile2ID)+len(event.EventID)+96)
	buf = append(buf, `{"eventId":"`...)
	buf = append(buf, event.EventID...)
	buf = append(buf, `","profile1Id":"`...)
	buf = append(buf, event.Profile1ID...)
	buf = append(buf, `","profile2Id":"`...)
	buf = append(buf, event.Profile2ID...)
	buf = append(buf, `","decision":`...)
	buf = strconv.AppendBool(buf, event.Decision)
	buf = append(buf, `,"isSuper":`...)
	buf = strconv.AppendBool(buf, event.IsSuper)
	buf = append(buf, `,"timestamp":`...)
	buf = strconv.AppendInt(buf, event.Timestamp, 10)
	buf = append(buf, '}')
	return buf
}

func uuidFromParts(msb, lsb uint64) string {
	var out [36]byte
	writeHex(out[0:8], uint32(msb>>32), 8)
	out[8] = '-'
	writeHex(out[9:13], uint32(msb>>16), 4)
	out[13] = '-'
	writeHex(out[14:18], uint32(msb), 4)
	out[18] = '-'
	writeHex(out[19:23], uint32(lsb>>48), 4)
	out[23] = '-'
	writeHex64(out[24:36], lsb, 12)
	return string(out[:])
}

func writeHex(dst []byte, value uint32, width int) {
	const alphabet = "0123456789abcdef"
	for i := width - 1; i >= 0; i-- {
		dst[i] = alphabet[value&0xf]
		value >>= 4
	}
}

func writeHex64(dst []byte, value uint64, width int) {
	const alphabet = "0123456789abcdef"
	for i := width - 1; i >= 0; i-- {
		dst[i] = alphabet[value&0xf]
		value >>= 4
	}
}

type ProfileCreateEvent struct {
	EventID   string     `json:"eventId"`
	ProfileID string     `json:"profileId"`
	Timestamp *time.Time `json:"timestamp"`
	UserID    *string    `json:"userId"`
}

type ProfileDeleteEvent struct {
	EventID   string     `json:"eventId"`
	ProfileID string     `json:"profileId"`
	Timestamp *time.Time `json:"timestamp"`
}
