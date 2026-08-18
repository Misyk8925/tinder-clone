package client

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/google/uuid"
)

type Profiles struct {
	baseURL string
	http    *http.Client
}

func NewProfiles(baseURL string) *Profiles {
	return &Profiles{baseURL: strings.TrimRight(baseURL, "/"), http: &http.Client{Timeout: 3 * time.Second}}
}

func (client *Profiles) Existing(ctx context.Context, ids []uuid.UUID, bearer string) ([]uuid.UUID, error) {
	values := make([]string, len(ids))
	for i, id := range ids {
		values[i] = id.String()
	}
	query := url.Values{"ids": []string{strings.Join(values, ",")}}
	var response []struct {
		ProfileID uuid.UUID `json:"profileId"`
	}
	if err := client.getJSON(ctx, client.baseURL+"/by-ids?"+query.Encode(), bearer, &response); err != nil {
		return nil, err
	}
	found := make([]uuid.UUID, 0, len(response))
	for _, item := range response {
		if item.ProfileID != uuid.Nil {
			found = append(found, item.ProfileID)
		}
	}
	return found, nil
}

func (client *Profiles) Mine(ctx context.Context, bearer string) (uuid.UUID, string, error) {
	var response struct {
		ProfileID uuid.UUID `json:"profileId"`
		UserID    string    `json:"userId"`
	}
	if err := client.getJSON(ctx, client.baseURL+"/me", bearer, &response); err != nil {
		return uuid.Nil, "", err
	}
	if response.ProfileID == uuid.Nil {
		return uuid.Nil, "", fmt.Errorf("profiles /me returned no profile ID")
	}
	return response.ProfileID, response.UserID, nil
}

func (client *Profiles) getJSON(ctx context.Context, endpoint, bearer string, dst any) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return err
	}
	if bearer != "" {
		req.Header.Set("Authorization", "Bearer "+bearer)
	}
	resp, err := client.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 4096))
		return fmt.Errorf("profiles returned status %d", resp.StatusCode)
	}
	return json.NewDecoder(io.LimitReader(resp.Body, 2<<20)).Decode(dst)
}
