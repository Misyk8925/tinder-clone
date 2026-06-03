package main

import (
	"time"

	"github.com/google/uuid"
)

type Location struct {
	ID        uuid.UUID
	City      string
	Lat       float64
	Lon       float64
	CreatedAt time.Time
	UpdatedAt time.Time
}

type ResolveRequest struct {
	City      string   `json:"city"`
	Latitude  *float64 `json:"latitude"`
	Longitude *float64 `json:"longitude"`
}

type LocationResponse struct {
	ID        uuid.UUID `json:"id"`
	City      string    `json:"city"`
	Latitude  float64   `json:"latitude"`
	Longitude float64   `json:"longitude"`
}

func toResponse(l *Location) LocationResponse {
	return LocationResponse{
		ID:        l.ID,
		City:      l.City,
		Latitude:  l.Lat,
		Longitude: l.Lon,
	}
}
