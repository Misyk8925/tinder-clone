package main

import (
	"context"
	"fmt"
	"log"
	"sync"

	"github.com/google/uuid"
	"golang.org/x/sync/singleflight"
)

// defaultLat / defaultLon: center of Europe — fallback when Nominatim is unavailable.
const (
	defaultLat = 50.0
	defaultLon = 10.0
)

type locationRepo interface {
	FindByCity(ctx context.Context, city string) (*Location, error)
	FindByID(ctx context.Context, id uuid.UUID) (*Location, error)
	Save(ctx context.Context, loc *Location) (*Location, error)
}

type LocationService struct {
	repo     locationRepo
	geocoder *Geocoder
	log      *log.Logger

	// L1: in-memory cache keyed by city name
	cache sync.Map // map[string]*Location

	// Singleflight: ensures only one goroutine resolves a new city at a time;
	// all others wait and share the result (replaces Java's per-city ReentrantLock map).
	sfGroup singleflight.Group
}

func NewLocationService(repo locationRepo, geocoder *Geocoder, logger *log.Logger) *LocationService {
	return &LocationService{repo: repo, geocoder: geocoder, log: logger}
}

// Resolve resolves a city name to a Location, creating it if not yet known.
func (s *LocationService) Resolve(ctx context.Context, city string) (*Location, error) {
	if city == "" {
		city = "Unknown"
	}

	// L1: fast path — no DB round-trip
	if v, ok := s.cache.Load(city); ok {
		return v.(*Location), nil
	}

	// Singleflight: only one goroutine does the DB + geocoder work per city
	key := "city:" + city
	val, err, _ := s.sfGroup.Do(key, func() (interface{}, error) {
		// Double-check L1 after acquiring the singleflight slot
		if v, ok := s.cache.Load(city); ok {
			return v, nil
		}
		return s.resolveUncached(ctx, city)
	})
	if err != nil {
		return nil, err
	}

	loc := val.(*Location)
	s.cache.Store(city, loc)
	return loc, nil
}

// ResolveFromCoords returns a Location for known GPS coordinates, deduplicating
// by city name when one is provided. This prevents location_db from accumulating
// a new row for every browser GPS update within the same city.
//
// If city is known ("Vienna", "Berlin", …):
//   - L1 cache hit → return immediately
//   - L2 DB hit    → cache and return the existing row; the stored coordinates
//     represent the city centre and are intentionally not overwritten by live GPS
//   - Miss          → insert the new row (first time we see this city via GPS)
//
// If city is empty or "Unknown" (no reverse-geocoding available):
//   - Always insert a new row, since there is no stable key to deduplicate on.
func (s *LocationService) ResolveFromCoords(ctx context.Context, lat, lon float64, city string) (*Location, error) {
	if city == "" {
		city = "Unknown"
	}

	// For a named city, try L1 → L2 before inserting anything.
	if city != "Unknown" {
		if v, ok := s.cache.Load(city); ok {
			return v.(*Location), nil
		}

		key := "city:" + city
		val, err, _ := s.sfGroup.Do(key, func() (interface{}, error) {
			if v, ok := s.cache.Load(city); ok {
				return v, nil
			}
			existing, err := s.repo.FindByCity(ctx, city)
			if err != nil {
				return nil, err
			}
			if existing != nil {
				s.log.Printf("coords L2 hit for city %q — reusing existing row", city)
				return existing, nil
			}
			// First time we see this city via GPS — insert with the provided coords.
			loc := &Location{ID: uuid.New(), City: city, Lat: lat, Lon: lon}
			return s.repo.Save(ctx, loc)
		})
		if err != nil {
			return nil, err
		}
		loc := val.(*Location)
		s.cache.Store(city, loc)
		return loc, nil
	}

	// No city name — insert a new row for this raw GPS fix.
	loc := &Location{ID: uuid.New(), City: city, Lat: lat, Lon: lon}
	return s.repo.Save(ctx, loc)
}

func (s *LocationService) GetByID(ctx context.Context, id uuid.UUID) (*Location, error) {
	return s.repo.FindByID(ctx, id)
}

func (s *LocationService) resolveUncached(ctx context.Context, city string) (*Location, error) {
	// L2: DB lookup
	existing, err := s.repo.FindByCity(ctx, city)
	if err != nil {
		return nil, err
	}
	if existing != nil {
		s.log.Printf("location L2 cache hit for city %q", city)
		return existing, nil
	}

	// L3: geocode
	lat, lon, geoErr := s.geocoder.Geocode(ctx, city)
	if geoErr != nil {
		s.log.Printf("geocoding failed for city %q, using default coordinates: %v", city, geoErr)
		lat, lon = defaultLat, defaultLon
	} else {
		s.log.Printf("geocoded city %q: lat=%f lon=%f", city, lat, lon)
	}

	loc := &Location{
		ID:   uuid.New(),
		City: city,
		Lat:  lat,
		Lon:  lon,
	}

	saved, err := s.repo.Save(ctx, loc)
	if err != nil {
		return nil, err
	}
	if saved == nil {
		// concurrent insert — read back
		saved, err = s.repo.FindByCity(ctx, city)
		if err != nil || saved == nil {
			return nil, fmt.Errorf("location not found after concurrent insert for city: %s", city)
		}
	}
	return saved, nil
}
