package model

import (
	"encoding/binary"
	"errors"
	"sync/atomic"
	"time"
)

var ErrQueueFull = errors.New("swipe producer queue is full")

type SwipeDTO struct {
	Profile1ID string `json:"profile1Id"`
	Profile2ID string `json:"profile2Id"`
	Decision   bool   `json:"decision"`
	IsSuper    *bool  `json:"isSuper"`
}

func (dto SwipeDTO) Super() bool { return dto.IsSuper != nil && *dto.IsSuper }

type SwipeCommand struct {
	Profile1ID string
	Profile2ID string
	Decision   bool
	IsSuper    bool
}

type SwipeCreatedEvent struct {
	EventID    [16]byte
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

func NewSwipeCreatedEvent(command SwipeCommand) SwipeCreatedEvent {
	var eventID [16]byte
	binary.BigEndian.PutUint64(eventID[:8], uint64(time.Now().UnixNano()))
	binary.BigEndian.PutUint64(eventID[8:], eventSequence.Add(1))
	eventID[6] = eventID[6]&0x0f | 0x40
	eventID[8] = eventID[8]&0x3f | 0x80
	return SwipeCreatedEvent{
		EventID: eventID, Profile1ID: command.Profile1ID, Profile2ID: command.Profile2ID,
		Decision: command.Decision, IsSuper: command.IsSuper, Timestamp: time.Now().UnixMilli(),
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

type HTTPError struct {
	Status int
	Reason string
}

func (err HTTPError) Error() string { return err.Reason }
