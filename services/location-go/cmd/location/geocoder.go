package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"math"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/sony/gobreaker"
)

type Geocoder struct {
	client       *http.Client
	baseURL      string
	userAgent    string
	countryCodes string
	cb           *gobreaker.CircuitBreaker
	log          *log.Logger
}

func NewGeocoder(cfg Config, logger *log.Logger) *Geocoder {
	cb := gobreaker.NewCircuitBreaker(gobreaker.Settings{
		Name:        "nominatim",
		MaxRequests: 5,
		Interval:    30 * time.Second,
		Timeout:     30 * time.Second,
		ReadyToTrip: func(counts gobreaker.Counts) bool {
			return counts.ConsecutiveFailures >= 5
		},
		OnStateChange: func(name string, from, to gobreaker.State) {
			logger.Printf("Nominatim circuit breaker: %s → %s", from, to)
		},
	})
	return &Geocoder{
		client:       &http.Client{Timeout: cfg.GeocodingTimeout()},
		baseURL:      strings.TrimRight(cfg.GeocodingBaseURL, "/"),
		userAgent:    cfg.GeocodingUserAgent,
		countryCodes: cfg.GeocodingCountries,
		cb:           cb,
		log:          logger,
	}
}

type nominatimResult struct {
	Lat string `json:"lat"`
	Lon string `json:"lon"`
}

// Geocode resolves a city name to coordinates. Returns (0, 0, err) when geocoding fails.
func (g *Geocoder) Geocode(ctx context.Context, city string) (lat, lon float64, err error) {
	city = strings.TrimSpace(city)
	if city == "" {
		return 0, 0, fmt.Errorf("city is empty")
	}

	type result struct {
		lat, lon float64
	}

	val, cbErr := g.cb.Execute(func() (interface{}, error) {
		return g.doGeocode(ctx, city)
	})

	if cbErr != nil {
		g.log.Printf("Geocoder circuit breaker error for city %q: %v", city, cbErr)
		return 0, 0, cbErr
	}

	r := val.(result)
	return r.lat, r.lon, nil
}

func (g *Geocoder) doGeocode(ctx context.Context, city string) (interface{}, error) {
	type result struct{ lat, lon float64 }

	q := url.Values{}
	q.Set("q", city)
	q.Set("format", "jsonv2")
	q.Set("limit", "1")
	q.Set("addressdetails", "1")
	if g.countryCodes != "" {
		q.Set("countrycodes", g.countryCodes)
	}

	reqURL := g.baseURL + "/search?" + q.Encode()

	var lastErr error
	backoff := 500 * time.Millisecond
	for attempt := 0; attempt < 3; attempt++ {
		if attempt > 0 {
			select {
			case <-ctx.Done():
				return nil, ctx.Err()
			case <-time.After(backoff):
				backoff = time.Duration(float64(backoff) * math.Phi)
			}
		}

		req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
		if err != nil {
			return nil, err
		}
		req.Header.Set("User-Agent", g.userAgent)
		req.Header.Set("Accept", "application/json")

		resp, err := g.client.Do(req)
		if err != nil {
			lastErr = err
			continue
		}

		var results []nominatimResult
		decErr := json.NewDecoder(resp.Body).Decode(&results)
		_ = resp.Body.Close()

		if decErr != nil {
			lastErr = decErr
			continue
		}

		if len(results) == 0 {
			return nil, fmt.Errorf("no results for city %q", city)
		}

		lat, errLat := strconv.ParseFloat(results[0].Lat, 64)
		lon, errLon := strconv.ParseFloat(results[0].Lon, 64)
		if errLat != nil || errLon != nil {
			return nil, fmt.Errorf("invalid coordinates from geocoder")
		}

		return result{lat: lat, lon: lon}, nil
	}

	return nil, fmt.Errorf("geocoding failed after retries: %w", lastErr)
}
