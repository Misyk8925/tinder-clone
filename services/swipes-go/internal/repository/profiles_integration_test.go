package repository

import (
	"context"
	"io"
	"log"
	"os"
	"testing"
	"time"

	"github.com/google/uuid"
)

func TestProfilesIntegration(t *testing.T) {
	databaseURL := os.Getenv("SWIPES_INTEGRATION_DATABASE_URL")
	redisAddr := os.Getenv("SWIPES_INTEGRATION_REDIS_ADDR")
	if databaseURL == "" || redisAddr == "" {
		t.Skip("set SWIPES_INTEGRATION_DATABASE_URL and SWIPES_INTEGRATION_REDIS_ADDR")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	repo, err := NewProfiles(ctx, redisAddr, databaseURL, "http://127.0.0.1:1", log.New(io.Discard, "", 0))
	if err != nil {
		t.Fatalf("open repository: %v", err)
	}
	defer repo.Close()
	first, second := uuid.New(), uuid.New()
	if err := repo.save(ctx, first, "user-1", time.Now()); err != nil {
		t.Fatal(err)
	}
	if err := repo.save(ctx, second, "user-2", time.Now()); err != nil {
		t.Fatal(err)
	}
	if err := repo.warmRedis(ctx, []uuid.UUID{first, second}); err != nil {
		t.Fatal(err)
	}
	owner, err := repo.ProfileIDForUser(ctx, "user-1", "unused")
	if err != nil || owner != first {
		t.Fatalf("owner=%s err=%v", owner, err)
	}
	exists, err := repo.ExistsAll(ctx, first, second, "unused")
	if err != nil || !exists {
		t.Fatalf("exists=%v err=%v", exists, err)
	}
}
