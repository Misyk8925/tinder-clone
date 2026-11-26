package client

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"tinder-clone/services/swipes-go/internal/model"
)

func TestMineWrapsNotFoundAsErrProfileNotFound(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	defer server.Close()

	c := NewProfiles(server.URL)
	_, _, err := c.Mine(context.Background(), "token")
	if !errors.Is(err, model.ErrProfileNotFound) {
		t.Fatalf("expected ErrProfileNotFound for 404 /me, got %v", err)
	}
}
