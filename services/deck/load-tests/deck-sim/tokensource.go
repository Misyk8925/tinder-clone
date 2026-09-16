package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

// TokenSource hands the driver a bearer token for one viewer.
type TokenSource interface {
	Token(viewerID string) (string, error)
}

// MintedTokens signs its own tokens. It needs no Keycloak users at all, which is what makes
// a hundred-thousand-viewer run possible — but every service in the path has to be
// configured to trust the simulator's issuer, and that trust must never leave a bench.
type MintedTokens struct {
	issuer *Issuer
}

func (m MintedTokens) Token(viewerID string) (string, error) {
	return m.issuer.Token(viewerID)
}

// KeycloakTokens asks the real Keycloak for a real token per viewer, using the direct access
// grant of an existing public client.
//
// Nothing in the platform has to be reconfigured: the tokens are issued by the issuer every
// service already trusts. The cost is that each viewer needs a Keycloak user, which is why
// this fits a small live run and the minted source fits a large one.
type KeycloakTokens struct {
	tokenURL    string
	clientID    string
	credentials map[string]Credential
	client      *http.Client

	mutex  sync.Mutex
	cached map[string]cachedToken
}

// Credential is one viewer's Keycloak login, keyed by the profile it belongs to.
type Credential struct {
	ProfileID string `json:"profileId"`
	Username  string `json:"username"`
	Password  string `json:"password"`
}

type cachedToken struct {
	token   string
	expires time.Time
}

func NewKeycloakTokens(tokenURL, clientID string, credentials []Credential) *KeycloakTokens {
	byProfile := make(map[string]Credential, len(credentials))
	for _, credential := range credentials {
		byProfile[credential.ProfileID] = credential
	}
	return &KeycloakTokens{
		tokenURL: tokenURL, clientID: clientID, credentials: byProfile,
		client: &http.Client{Timeout: 20 * time.Second},
		cached: make(map[string]cachedToken),
	}
}

// Viewers are the profiles this source can actually speak for. A run drives these and no
// others, because a viewer without a credential has no way to authenticate.
func (k *KeycloakTokens) Viewers() map[string]bool {
	viewers := make(map[string]bool, len(k.credentials))
	for profileID := range k.credentials {
		viewers[profileID] = true
	}
	return viewers
}

func (k *KeycloakTokens) Token(viewerID string) (string, error) {
	k.mutex.Lock()
	if cached, ok := k.cached[viewerID]; ok && time.Now().Before(cached.expires) {
		k.mutex.Unlock()
		return cached.token, nil
	}
	credential, known := k.credentials[viewerID]
	k.mutex.Unlock()

	if !known {
		return "", fmt.Errorf("no credential for viewer %s", viewerID)
	}

	form := url.Values{}
	form.Set("grant_type", "password")
	form.Set("client_id", k.clientID)
	form.Set("username", credential.Username)
	form.Set("password", credential.Password)

	response, err := k.client.Post(k.tokenURL, "application/x-www-form-urlencoded",
		strings.NewReader(form.Encode()))
	if err != nil {
		return "", fmt.Errorf("token request for %s: %w", credential.Username, err)
	}
	defer response.Body.Close()
	body, _ := io.ReadAll(response.Body)

	if response.StatusCode != http.StatusOK {
		return "", fmt.Errorf("token request for %s returned %d: %s",
			credential.Username, response.StatusCode, truncate(body))
	}

	var payload struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int    `json:"expires_in"`
	}
	if err := json.Unmarshal(body, &payload); err != nil {
		return "", fmt.Errorf("decode token for %s: %w", credential.Username, err)
	}

	lifetime := time.Duration(payload.ExpiresIn) * time.Second
	if lifetime <= 0 {
		lifetime = time.Minute
	}
	k.mutex.Lock()
	// Refreshed a little before it expires so a long run does not fail mid-deck.
	k.cached[viewerID] = cachedToken{payload.AccessToken, time.Now().Add(lifetime - 30*time.Second)}
	k.mutex.Unlock()

	return payload.AccessToken, nil
}
