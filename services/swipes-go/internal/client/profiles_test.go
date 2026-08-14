package client

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/google/uuid"
)

func TestMineForwardsBearerAndBoundsContract(t *testing.T) {
	profileID := uuid.New()
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		if request.URL.Path != "/api/v1/profiles/me" || request.Header.Get("Authorization") != "Bearer token" {
			http.Error(response, "unexpected request", http.StatusBadRequest)
			return
		}
		_, _ = response.Write([]byte(`{"profileId":"` + profileID.String() + `","userId":"user-1","ignored":true}`))
	}))
	defer server.Close()
	client := NewProfiles(server.URL + "/api/v1/profiles")
	actualID, userID, err := client.Mine(context.Background(), "token")
	if err != nil || actualID != profileID || userID != "user-1" {
		t.Fatalf("id=%s user=%s err=%v", actualID, userID, err)
	}
}
