package main

import (
	"context"
	"errors"

	"github.com/google/uuid"
)

type ProfileExistenceChecker interface {
	ExistsAll(ctx context.Context, profile1ID, profile2ID uuid.UUID, bearerToken string) (bool, error)
}

type SwipeService struct {
	producer              SwipeEventProducer
	profileCache          ProfileExistenceChecker
	internalBypassProfile bool
}

func NewSwipeService(producer SwipeEventProducer, profileCache ProfileExistenceChecker, internalBypassProfile bool) *SwipeService {
	return &SwipeService{
		producer:              producer,
		profileCache:          profileCache,
		internalBypassProfile: internalBypassProfile,
	}
}

func (service *SwipeService) SendSwipe(ctx context.Context, dto SwipeDTO, isPremiumOrAdmin bool, bearerToken string, internalRequest bool) error {
	if dto.super() && !isPremiumOrAdmin {
		return forbidden("Super like requires a premium or admin account")
	}

	trustedBenchmarkRequest := internalRequest && service.internalBypassProfile
	if trustedBenchmarkRequest {
		if dto.Profile1ID == dto.Profile2ID {
			return badRequest("profile1Id and profile2Id must be different")
		}
		return service.enqueue(ctx, dto.Profile1ID, dto.Profile2ID, dto.Decision, dto.super())
	}

	if !internalRequest && bearerToken == "" {
		return unauthorized("Missing JWT principal")
	}

	profile1ID, err := parseProfileID(dto.Profile1ID, "profile1Id")
	if err != nil {
		return err
	}
	profile2ID, err := parseProfileID(dto.Profile2ID, "profile2Id")
	if err != nil {
		return err
	}
	if profile1ID == profile2ID {
		return badRequest("profile1Id and profile2Id must be different")
	}

	if service.profileCache == nil {
		return notFound("One or both profiles were not found")
	}
	exists, err := service.profileCache.ExistsAll(ctx, profile1ID, profile2ID, bearerToken)
	if err != nil {
		return err
	}
	if !exists {
		return notFound("One or both profiles were not found")
	}
	return service.enqueue(ctx, dto.Profile1ID, dto.Profile2ID, dto.Decision, dto.super())
}

func (service *SwipeService) SendTrustedInternalSwipe(ctx context.Context, swipe TrustedSwipe, isPremiumOrAdmin bool) error {
	if swipe.IsSuper && !isPremiumOrAdmin {
		return forbidden("Super like requires a premium or admin account")
	}
	if swipe.Profile1ID == swipe.Profile2ID {
		return badRequest("profile1Id and profile2Id must be different")
	}
	return service.enqueue(ctx, swipe.Profile1ID, swipe.Profile2ID, swipe.Decision, swipe.IsSuper)
}

func (service *SwipeService) enqueue(ctx context.Context, profile1ID, profile2ID string, decision, isSuper bool) error {
	command := SwipeCommand{
		Profile1ID: profile1ID,
		Profile2ID: profile2ID,
		Decision:   decision,
		IsSuper:    isSuper,
	}
	if err := service.producer.Send(ctx, command); err != nil {
		if errors.Is(err, errProducerQueueFull) {
			return tooManyRequests("Swipe producer queue is full")
		}
		return err
	}
	return nil
}

func parseProfileID(rawID, fieldName string) (uuid.UUID, error) {
	parsed, err := uuid.Parse(rawID)
	if err != nil {
		return uuid.Nil, badRequest("Invalid UUID in field: " + fieldName)
	}
	return parsed, nil
}
