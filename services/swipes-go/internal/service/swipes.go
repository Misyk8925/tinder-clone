package service

import (
	"context"
	"errors"
	"net/http"

	"github.com/google/uuid"
	"tinder-clone/services/swipes-go/internal/metrics"
	"tinder-clone/services/swipes-go/internal/model"
	"tinder-clone/services/swipes-go/internal/security"
)

type Producer interface {
	Send(context.Context, model.SwipeCommand) error
}

type Profiles interface {
	ExistsAll(context.Context, uuid.UUID, uuid.UUID, string) (bool, error)
	ProfileIDForUser(context.Context, string, string) (uuid.UUID, error)
}

type Swipes struct {
	producer             Producer
	profiles             Profiles
	metrics              *metrics.Metrics
	benchmarkBypassCheck bool
}

func New(producer Producer, profiles Profiles, serviceMetrics *metrics.Metrics, benchmarkBypassCheck bool) *Swipes {
	return &Swipes{producer: producer, profiles: profiles, metrics: serviceMetrics, benchmarkBypassCheck: benchmarkBypassCheck}
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
		return service.enqueue(ctx, dto)
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
	return service.enqueue(ctx, dto)
}

func (service *Swipes) enqueue(ctx context.Context, dto model.SwipeDTO) error {
	err := service.producer.Send(ctx, model.SwipeCommand{
		Profile1ID: dto.Profile1ID, Profile2ID: dto.Profile2ID, Decision: dto.Decision, IsSuper: dto.Super(),
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
