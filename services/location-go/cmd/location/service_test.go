package main

import (
	"context"
	"log"
	"os"
	"testing"

	"github.com/google/uuid"
)

// ---------------------------------------------------------------------------
// Fake repository — captures Save calls so we can assert deduplication.
// ---------------------------------------------------------------------------

type fakeRepo struct {
	byCity map[string]*Location
	byID   map[uuid.UUID]*Location
	saves  int
}

func newFakeRepo() *fakeRepo {
	return &fakeRepo{byCity: map[string]*Location{}, byID: map[uuid.UUID]*Location{}}
}

func (r *fakeRepo) FindByCity(_ context.Context, city string) (*Location, error) {
	return r.byCity[city], nil
}

func (r *fakeRepo) FindByID(_ context.Context, id uuid.UUID) (*Location, error) {
	return r.byID[id], nil
}

func (r *fakeRepo) Save(_ context.Context, loc *Location) (*Location, error) {
	r.saves++
	r.byCity[loc.City] = loc
	r.byID[loc.ID] = loc
	return loc, nil
}

// fakeGeocoder always returns a fixed point so tests don't hit the network.
type fakeGeocoder struct{}

func (g *fakeGeocoder) Geocode(_ context.Context, _ string) (lat, lon float64, err error) {
	return 48.2, 16.37, nil
}

func newTestService(repo locationRepo) *LocationService {
	return &LocationService{
		repo:     repo,
		geocoder: &Geocoder{}, // not reached in ResolveFromCoords path
		log:      log.New(os.Stdout, "test ", 0),
	}
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

// ResolveFromCoords with a known city should reuse the existing DB row rather
// than inserting a duplicate on every GPS update.
func TestResolveFromCoords_DeduplicatesByCity(t *testing.T) {
	repo := newFakeRepo()
	svc := newTestService(repo)
	// Inject the fake geocoder so Resolve works if called.
	svc.geocoder = &Geocoder{} // not used in ResolveFromCoords path

	ctx := context.Background()

	// First call — row does not exist yet, must insert.
	loc1, err := svc.ResolveFromCoords(ctx, 48.20, 16.37, "Vienna")
	if err != nil {
		t.Fatalf("first call: %v", err)
	}
	if repo.saves != 1 {
		t.Fatalf("expected 1 DB save after first call, got %d", repo.saves)
	}

	// Second call — same city, slightly different GPS (user still in Vienna).
	// Must NOT insert a second row.
	loc2, err := svc.ResolveFromCoords(ctx, 48.21, 16.38, "Vienna")
	if err != nil {
		t.Fatalf("second call: %v", err)
	}
	if repo.saves != 1 {
		t.Fatalf("expected still 1 DB save after second call (dedup), got %d", repo.saves)
	}
	if loc1.ID != loc2.ID {
		t.Fatalf("expected same location ID on dedup: got %v vs %v", loc1.ID, loc2.ID)
	}
}

// L1 cache: after the first resolve, no DB round-trip should occur.
func TestResolveFromCoords_L1CacheHit(t *testing.T) {
	repo := newFakeRepo()
	svc := newTestService(repo)
	svc.geocoder = &Geocoder{}

	ctx := context.Background()

	_, _ = svc.ResolveFromCoords(ctx, 48.20, 16.37, "Vienna") // populates L1
	savesAfterFirst := repo.saves

	_, _ = svc.ResolveFromCoords(ctx, 48.21, 16.38, "Vienna") // should hit L1
	if repo.saves != savesAfterFirst {
		t.Fatalf("expected no additional DB save on L1 hit, saves went from %d to %d",
			savesAfterFirst, repo.saves)
	}
}

// Unknown / empty city must always insert so raw GPS fixes are not conflated.
func TestResolveFromCoords_UnknownCityAlwaysInserts(t *testing.T) {
	repo := newFakeRepo()
	svc := newTestService(repo)
	svc.geocoder = &Geocoder{}

	ctx := context.Background()

	_, _ = svc.ResolveFromCoords(ctx, 48.20, 16.37, "")
	_, _ = svc.ResolveFromCoords(ctx, 48.21, 16.38, "")

	if repo.saves != 2 {
		t.Fatalf("expected 2 inserts for unknown city, got %d", repo.saves)
	}
}

// Different cities must each get their own row.
func TestResolveFromCoords_DifferentCitiesGetSeparateRows(t *testing.T) {
	repo := newFakeRepo()
	svc := newTestService(repo)
	svc.geocoder = &Geocoder{}

	ctx := context.Background()

	locV, _ := svc.ResolveFromCoords(ctx, 48.20, 16.37, "Vienna")
	locB, _ := svc.ResolveFromCoords(ctx, 52.52, 13.40, "Berlin")

	if locV.ID == locB.ID {
		t.Fatal("Vienna and Berlin should have different IDs")
	}
	if repo.saves != 2 {
		t.Fatalf("expected 2 DB saves for 2 distinct cities, got %d", repo.saves)
	}
}
