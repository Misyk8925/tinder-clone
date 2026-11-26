package service

import (
	"context"
	"errors"
	"net/http"
	"time"

	"github.com/google/uuid"
	"tinder-clone/services/swipes-go/internal/metrics"
	"tinder-clone/services/swipes-go/internal/model"
	"tinder-clone/services/swipes-go/internal/security"
)

type Producer interface {
	Send(context.Context, model.SwipeCommand) error
}

type RevokedPublisher interface {
	PublishRevoked(context.Context, model.SwipeRevokedEvent) error
}

type Profiles interface {
	ExistsAll(context.Context, uuid.UUID, uuid.UUID, string) (bool, error)
	ProfileIDForUser(context.Context, string, string) (uuid.UUID, error)
}

type RewindRepository interface {
	RecordSwipe(context.Context, model.SwipeHistoryRecord) error
	RewindLastSwipe(
		ctx context.Context,
		profile1ID uuid.UUID,
		expectedSwipeEventID *uuid.UUID,
		now time.Time,
		window time.Duration,
		publish func(context.Context, model.SwipeRevokedEvent) error,
	) (model.RewindResponse, error)
}

type Swipes struct {
	producer             Producer
	revokedPublisher     RevokedPublisher
	profiles             Profiles
	rewindStore          RewindRepository
	rewindWindow         time.Duration
	now                  func() time.Time
	metrics              *metrics.Metrics
	benchmarkBypassCheck bool
}

func New(producer Producer, profiles Profiles, serviceMetrics *metrics.Metrics, benchmarkBypassCheck bool) *Swipes {
	return &Swipes{
		producer:             producer,
		profiles:             profiles,
		rewindWindow:         5 * time.Minute,
		now:                  time.Now,
		metrics:              serviceMetrics,
		benchmarkBypassCheck: benchmarkBypassCheck,
	}
}

func (service *Swipes) WithRewind(store RewindRepository, publisher RevokedPublisher, window time.Duration, clock func() time.Time) *Swipes {
	service.rewindStore = store
	service.revokedPublisher = publisher
	if window > 0 {
		service.rewindWindow = window
	}
	if clock != nil {
		service.now = clock
	}
	return service
}

func (service *Swipes) Send(ctx context.Context, dto model.SwipeDTO, principal security.Principal, superRoute bool) error {
	isPrivileged := principal.Benchmark || principal.HasAnyRole("USER_PREMIUM", "ADMIN")
	if superRoute && !isPrivileged {
		return service.reject(http.StatusForbidden, "Premium or admin role is required")
	}
	if dto.Super() && !isPrivileged {
		return service.reject(http.StatusForbidden, "Super like requires a premium or admin account")
	}
	if principal.Benchmark && service.benchmarkBypassCheck {
		if dto.Profile1ID == "" || dto.Profile2ID == "" {
			return service.reject(http.StatusBadRequest, "Profile IDs are required")
		}
		if dto.Profile1ID == dto.Profile2ID {
			return service.reject(http.StatusBadRequest, "profile1Id and profile2Id must be different")
		}
		return service.enqueue(ctx, dto, [16]byte{}, 0)
	}

	profile1ID, err := parseProfileID(dto.Profile1ID, "profile1Id")
	if err != nil {
		return service.rejectError(err)
	}
	profile2ID, err := parseProfileID(dto.Profile2ID, "profile2Id")
	if err != nil {
		return service.rejectError(err)
	}
	if profile1ID == profile2ID {
		return service.reject(http.StatusBadRequest, "profile1Id and profile2Id must be different")
	}
	if !principal.Benchmark {
		ownerID, err := service.profiles.ProfileIDForUser(ctx, principal.Subject, principal.Bearer)
		if err != nil {
			return service.reject(http.StatusServiceUnavailable, "Profile ownership could not be verified")
		}
		if ownerID != profile1ID {
			service.metrics.OwnershipDenied.Add(1)
			return service.reject(http.StatusForbidden, "profile1Id does not belong to the authenticated user")
		}
	}
	exists, err := service.profiles.ExistsAll(ctx, profile1ID, profile2ID, principal.Bearer)
	if err != nil {
		return service.reject(http.StatusServiceUnavailable, "Profile verification is unavailable")
	}
	if !exists {
		return service.reject(http.StatusNotFound, "One or both profiles were not found")
	}

	eventID := model.NewEventID()
	swipedAt := service.now().UTC()
	if service.rewindStore != nil {
		record := model.SwipeHistoryRecord{
			EventID:    uuid.UUID(eventID),
			Profile1ID: profile1ID,
			Profile2ID: profile2ID,
			Decision:   dto.Decision,
			IsSuper:    dto.Super(),
			SwipedAt:   swipedAt,
		}
		if err := service.rewindStore.RecordSwipe(ctx, record); err != nil {
			return service.reject(http.StatusServiceUnavailable, "Swipe history store is unavailable")
		}
	}
	return service.enqueue(ctx, dto, eventID, swipedAt.UnixMilli())
}

func (service *Swipes) Rewind(ctx context.Context, req model.RewindRequest, principal security.Principal) (model.RewindResponse, error) {
	if service.rewindStore == nil || service.revokedPublisher == nil {
		service.metrics.RewindRejected.Add(1)
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusServiceUnavailable,
			Code:   "SERVICE_UNAVAILABLE",
			Reason: "Rewind service is temporarily unavailable",
		}
	}

	var expectedEventID *uuid.UUID
	if req.SwipeEventID != "" {
		parsed, err := uuid.Parse(req.SwipeEventID)
		if err != nil {
			service.metrics.RewindRejected.Add(1)
			return model.RewindResponse{}, model.HTTPError{
				Status: http.StatusBadRequest,
				Code:   "INVALID_PAYLOAD",
				Reason: "Invalid UUID in field swipeEventId",
			}
		}
		expectedEventID = &parsed
	}

	ownerID, err := service.profiles.ProfileIDForUser(ctx, principal.Subject, principal.Bearer)
	if err != nil {
		service.metrics.RewindRejected.Add(1)
		if errors.Is(err, model.ErrProfileNotFound) {
			service.metrics.OwnershipDenied.Add(1)
			return model.RewindResponse{}, model.HTTPError{
				Status: http.StatusForbidden,
				Code:   "PROFILE_OWNERSHIP_DENIED",
				Reason: "Authenticated user does not have an active profile",
			}
		}
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusServiceUnavailable,
			Code:   "SERVICE_UNAVAILABLE",
			Reason: "Profile ownership could not be verified",
		}
	}
	if ownerID == uuid.Nil {
		service.metrics.RewindRejected.Add(1)
		service.metrics.OwnershipDenied.Add(1)
		return model.RewindResponse{}, model.HTTPError{
			Status: http.StatusForbidden,
			Code:   "PROFILE_OWNERSHIP_DENIED",
			Reason: "Authenticated user does not have an active profile",
		}
	}

	resp, err := service.rewindStore.RewindLastSwipe(
		ctx,
		ownerID,
		expectedEventID,
		service.now().UTC(),
		service.rewindWindow,
		service.revokedPublisher.PublishRevoked,
	)
	if err != nil {
		service.metrics.RewindRejected.Add(1)
		return model.RewindResponse{}, err
	}
	if resp.AlreadyRevoked {
		service.metrics.RevokedIdempotent.Add(1)
	} else {
		service.metrics.Revoked.Add(1)
	}
	return resp, nil
}

func (service *Swipes) enqueue(ctx context.Context, dto model.SwipeDTO, eventID [16]byte, timestamp int64) error {
	err := service.producer.Send(ctx, model.SwipeCommand{
		EventID: eventID, Profile1ID: dto.Profile1ID, Profile2ID: dto.Profile2ID,
		Decision: dto.Decision, IsSuper: dto.Super(), Timestamp: timestamp,
	})
	if errors.Is(err, model.ErrQueueFull) {
		service.metrics.QueueFull.Add(1)
		return service.reject(http.StatusTooManyRequests, "Swipe producer queue is full")
	}
	if err != nil {
		return service.reject(http.StatusServiceUnavailable, "Swipe producer is unavailable")
	}
	service.metrics.Accepted.Add(1)
	return nil
}

func (service *Swipes) reject(status int, reason string) error {
	service.metrics.Rejected.Add(1)
	return model.HTTPError{Status: status, Reason: reason}
}

func (service *Swipes) rejectError(err error) error {
	service.metrics.Rejected.Add(1)
	return err
}

func parseProfileID(rawID, fieldName string) (uuid.UUID, error) {
	parsed, err := uuid.Parse(rawID)
	if err != nil {
		return uuid.Nil, model.HTTPError{Status: http.StatusBadRequest, Reason: "Invalid UUID in field: " + fieldName}
	}
	return parsed, nil
}
