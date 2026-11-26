package model

import (
	"encoding/binary"
	"errors"
	"sync/atomic"
	"time"

	"github.com/google/uuid"
)

var (
	ErrQueueFull        = errors.New("swipe producer queue is full")
	ErrProfileNotFound  = errors.New("profile not found for authenticated user")
)

type SwipeDTO struct {
	Profile1ID string `json:"profile1Id"`
	Profile2ID string `json:"profile2Id"`
	Decision   bool   `json:"decision"`
	IsSuper    *bool  `json:"isSuper"`
}

func (dto SwipeDTO) Super() bool { return dto.IsSuper != nil && *dto.IsSuper }

type SwipeCommand struct {
	EventID    [16]byte
	Profile1ID string
	Profile2ID string
	Decision   bool
	IsSuper    bool
	Timestamp  int64
}

type SwipeCreatedEvent struct {
	EventID    [16]byte
	Profile1ID string
	Profile2ID string
	Decision   bool
	IsSuper    bool
	Timestamp  int64
}

type RewindRequest struct {
	SwipeEventID string `json:"swipeEventId,omitempty"`
}

type RewindResponse struct {
	RevokedSwipeEventID string    `json:"revokedSwipeEventId"`
	RevocationEventID   string    `json:"revocationEventId"`
	Profile2ID          string    `json:"profile2Id"`
	Decision            bool      `json:"decision"`
	RevokedAt           time.Time `json:"revokedAt"`
	AlreadyRevoked      bool      `json:"alreadyRevoked"`
}

type RewindErrorResponse struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type SwipeRevokedEvent struct {
	EventID             string `json:"eventId"`
	RevokedSwipeEventID string `json:"revokedSwipeEventId"`
	Profile1ID          string `json:"profile1Id"`
	Profile2ID          string `json:"profile2Id"`
	OriginalDecision    bool   `json:"originalDecision"`
	SwipedAt            int64  `json:"swipedAt"`
	RevokedAt           int64  `json:"revokedAt"`
}

type MatchCreatedEvent struct {
	EventID    string          `json:"eventId"`
	Profile1ID string          `json:"profile1Id"`
	Profile2ID string          `json:"profile2Id"`
	CreatedAt  *EventTimestamp `json:"createdAt"`
}

type SwipeHistoryRecord struct {
	EventID    uuid.UUID
	Profile1ID uuid.UUID
	Profile2ID uuid.UUID
	Decision   bool
	IsSuper    bool
	SwipedAt   time.Time
}

var eventSequence atomic.Uint64

func init() {
	eventSequence.Store(uint64(time.Now().UnixNano()))
}

func NewEventID() [16]byte {
	var eventID [16]byte
	binary.BigEndian.PutUint64(eventID[:8], uint64(time.Now().UnixNano()))
	binary.BigEndian.PutUint64(eventID[8:], eventSequence.Add(1))
	eventID[6] = eventID[6]&0x0f | 0x40
	eventID[8] = eventID[8]&0x3f | 0x80
	return eventID
}

func NewSwipeCreatedEvent(command SwipeCommand) SwipeCreatedEvent {
	eventID := command.EventID
	if eventID == ([16]byte{}) {
		eventID = NewEventID()
	}
	ts := command.Timestamp
	if ts <= 0 {
		ts = time.Now().UnixMilli()
	}
	return SwipeCreatedEvent{
		EventID: eventID, Profile1ID: command.Profile1ID, Profile2ID: command.Profile2ID,
		Decision: command.Decision, IsSuper: command.IsSuper, Timestamp: ts,
	}
}

type ProfileCreateEvent struct {
	EventID   string          `json:"eventId"`
	ProfileID string          `json:"profileId"`
	Timestamp *EventTimestamp `json:"timestamp"`
	UserID    *string         `json:"userId"`
}

type ProfileDeleteEvent struct {
	EventID   string          `json:"eventId"`
	ProfileID string          `json:"profileId"`
	Timestamp *EventTimestamp `json:"timestamp"`
}

type HTTPError struct {
	Status int
	Code   string
	Reason string
}

func (err HTTPError) Error() string { return err.Reason }
