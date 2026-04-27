package main

import (
	"context"
	"crypto/rsa"
	"crypto/subtle"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"math/big"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const internalAuthHeader = "X-Internal-Auth"

type Authenticator struct {
	internalSecret []byte
	jwtValidator   *JWTValidator
}

func NewAuthenticator(cfg Config, logger *log.Logger) *Authenticator {
	return &Authenticator{
		internalSecret: []byte(cfg.InternalAuthSecret),
		jwtValidator:   NewJWTValidator(cfg.JWKSetURL, logger),
	}
}

func (auth *Authenticator) IsInternalRequest(r *http.Request) bool {
	return auth.IsInternalCandidate(r.Header.Get(internalAuthHeader))
}

func (auth *Authenticator) IsInternalCandidate(candidate string) bool {
	candidate = strings.TrimSpace(candidate)
	if len(auth.internalSecret) == 0 || candidate == "" {
		return false
	}
	if len(candidate) != len(auth.internalSecret) {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(candidate), auth.internalSecret) == 1
}

func (auth *Authenticator) BearerToken(ctx context.Context, r *http.Request) (string, error) {
	return auth.BearerTokenHeader(ctx, r.Header.Get("Authorization"))
}

func (auth *Authenticator) BearerTokenHeader(ctx context.Context, header string) (string, error) {
	header = strings.TrimSpace(header)
	if header == "" {
		return "", unauthorized("Missing JWT principal")
	}
	parts := strings.SplitN(header, " ", 2)
	if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") || strings.TrimSpace(parts[1]) == "" {
		return "", unauthorized("Missing JWT principal")
	}
	token := strings.TrimSpace(parts[1])
	if err := auth.jwtValidator.Validate(ctx, token); err != nil {
		return "", unauthorized("Invalid JWT principal")
	}
	return token, nil
}

type JWTValidator struct {
	jwkSetURL string
	logger    *log.Logger
	client    *http.Client
	mu        sync.RWMutex
	keys      map[string]*rsa.PublicKey
	expiresAt time.Time
}

func NewJWTValidator(jwkSetURL string, logger *log.Logger) *JWTValidator {
	return &JWTValidator{
		jwkSetURL: strings.TrimSpace(jwkSetURL),
		logger:    logger,
		client:    &http.Client{Timeout: 5 * time.Second},
		keys:      map[string]*rsa.PublicKey{},
	}
}

func (validator *JWTValidator) Validate(ctx context.Context, rawToken string) error {
	if validator.jwkSetURL == "" {
		return errors.New("jwk set url is not configured")
	}
	if err := validator.ensureKeys(ctx, false); err != nil {
		return err
	}
	parser := jwt.NewParser(jwt.WithValidMethods([]string{"RS256", "RS384", "RS512"}))
	token, err := parser.Parse(rawToken, func(token *jwt.Token) (interface{}, error) {
		kid, _ := token.Header["kid"].(string)
		if kid == "" {
			return nil, errors.New("missing jwt kid")
		}
		if key := validator.key(kid); key != nil {
			return key, nil
		}
		if err := validator.ensureKeys(ctx, true); err != nil {
			return nil, err
		}
		if key := validator.key(kid); key != nil {
			return key, nil
		}
		return nil, fmt.Errorf("unknown jwt kid %q", kid)
	})
	if err != nil {
		return err
	}
	if token == nil || !token.Valid {
		return errors.New("invalid jwt")
	}
	return nil
}

func (validator *JWTValidator) key(kid string) *rsa.PublicKey {
	validator.mu.RLock()
	defer validator.mu.RUnlock()
	return validator.keys[kid]
}

func (validator *JWTValidator) ensureKeys(ctx context.Context, force bool) error {
	validator.mu.RLock()
	hasFreshKeys := len(validator.keys) > 0 && time.Now().Before(validator.expiresAt)
	validator.mu.RUnlock()
	if hasFreshKeys && !force {
		return nil
	}

	validator.mu.Lock()
	defer validator.mu.Unlock()
	if !force && len(validator.keys) > 0 && time.Now().Before(validator.expiresAt) {
		return nil
	}
	keys, ttl, err := validator.fetchKeys(ctx)
	if err != nil {
		return err
	}
	validator.keys = keys
	validator.expiresAt = time.Now().Add(ttl)
	return nil
}

func (validator *JWTValidator) fetchKeys(ctx context.Context) (map[string]*rsa.PublicKey, time.Duration, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, validator.jwkSetURL, nil)
	if err != nil {
		return nil, 0, err
	}
	resp, err := validator.client.Do(req)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, 0, fmt.Errorf("jwk set returned status %d", resp.StatusCode)
	}
	raw, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return nil, 0, err
	}
	var set jwkSet
	if err := json.Unmarshal(raw, &set); err != nil {
		return nil, 0, err
	}
	keys := make(map[string]*rsa.PublicKey, len(set.Keys))
	for _, key := range set.Keys {
		if key.KID == "" || key.KTY != "RSA" || key.N == "" || key.E == "" {
			continue
		}
		publicKey, err := rsaPublicKey(key.N, key.E)
		if err != nil {
			validator.logger.Printf("failed to parse jwk kid=%s: %v", key.KID, err)
			continue
		}
		keys[key.KID] = publicKey
	}
	if len(keys) == 0 {
		return nil, 0, errors.New("jwk set did not contain rsa keys")
	}
	return keys, 10 * time.Minute, nil
}

type jwkSet struct {
	Keys []jwkKey `json:"keys"`
}

type jwkKey struct {
	KID string `json:"kid"`
	KTY string `json:"kty"`
	N   string `json:"n"`
	E   string `json:"e"`
}

func rsaPublicKey(nValue, eValue string) (*rsa.PublicKey, error) {
	nBytes, err := base64.RawURLEncoding.DecodeString(nValue)
	if err != nil {
		return nil, err
	}
	eBytes, err := base64.RawURLEncoding.DecodeString(eValue)
	if err != nil {
		return nil, err
	}
	exponent := 0
	for _, b := range eBytes {
		exponent = exponent<<8 + int(b)
	}
	if exponent == 0 {
		return nil, errors.New("invalid exponent")
	}
	return &rsa.PublicKey{N: new(big.Int).SetBytes(nBytes), E: exponent}, nil
}
