package security

import (
	"context"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"tinder-clone/services/swipes-go/internal/utils"
)

type Principal struct {
	Subject   string
	Bearer    string
	Roles     map[string]struct{}
	Benchmark bool
}

func (p Principal) HasAnyRole(roles ...string) bool {
	for _, role := range roles {
		if _, ok := p.Roles[strings.ToUpper(role)]; ok {
			return true
		}
	}
	return false
}

type Authenticator struct {
	benchmarkSecret []byte
	validator       *JWTValidator
	cache           *principalCache
}

func NewAuthenticator(benchmarkSecret string, validator *JWTValidator) *Authenticator {
	return &Authenticator{benchmarkSecret: []byte(benchmarkSecret), validator: validator, cache: newPrincipalCache(10_000)}
}

func (auth *Authenticator) Authenticate(ctx context.Context, authorization, internal []byte) (Principal, error) {
	if utils.ConstantTimeEqual(internal, auth.benchmarkSecret) {
		return Principal{Benchmark: true, Roles: map[string]struct{}{"ADMIN": {}}}, nil
	}
	tokenBytes, err := utils.BearerTokenBytes(authorization)
	if err != nil {
		return Principal{}, err
	}
	tokenKey := sha256.Sum256(tokenBytes)
	if principal, ok := auth.cache.get(tokenKey); ok {
		return principal, nil
	}
	if auth.validator == nil {
		return Principal{}, errors.New("JWT validator is unavailable")
	}
	token := string(tokenBytes)
	claims, err := auth.validator.Validate(ctx, token)
	if err != nil {
		return Principal{}, err
	}
	roles := make(map[string]struct{}, len(claims.RealmAccess.Roles))
	addRoles(roles, claims.RealmAccess.Roles)
	for _, access := range claims.ResourceAccess {
		addRoles(roles, access.Roles)
	}
	principal := Principal{Subject: claims.Subject, Bearer: token, Roles: roles}
	auth.cache.put(tokenKey, principal, claims.ExpiresAt.Time)
	return principal, nil
}

func addRoles(dst map[string]struct{}, roles []string) {
	for _, role := range roles {
		dst[strings.ToUpper(role)] = struct{}{}
	}
}

type keycloakAccess struct {
	Roles []string `json:"roles"`
}

type Claims struct {
	jwt.RegisteredClaims
	RealmAccess    keycloakAccess            `json:"realm_access"`
	ResourceAccess map[string]keycloakAccess `json:"resource_access"`
}

type JWTValidator struct {
	jwkSetURL          string
	parser             *jwt.Parser
	client             *http.Client
	mu                 sync.RWMutex
	keys               map[string]*rsa.PublicKey
	expiresAt          time.Time
	generation         uint64
	nextUnknownRefresh time.Time
}

func NewJWTValidator(jwkSetURL, issuer, audience string) *JWTValidator {
	return &JWTValidator{
		jwkSetURL: strings.TrimSpace(jwkSetURL),
		parser: jwt.NewParser(
			jwt.WithValidMethods([]string{"RS256"}),
			jwt.WithIssuer(issuer),
			jwt.WithAudience(audience),
			jwt.WithExpirationRequired(),
			jwt.WithStrictDecoding(),
		),
		client: &http.Client{Timeout: 5 * time.Second},
		keys:   make(map[string]*rsa.PublicKey),
	}
}

func (validator *JWTValidator) Initialize(ctx context.Context) error {
	return validator.ensureKeys(ctx)
}

func (validator *JWTValidator) Validate(ctx context.Context, rawToken string) (Claims, error) {
	if err := validator.ensureKeys(ctx); err != nil {
		return Claims{}, err
	}
	var claims Claims
	token, err := validator.parser.ParseWithClaims(rawToken, &claims, validator.keyFunc(ctx))
	if err != nil || token == nil || !token.Valid || strings.TrimSpace(claims.Subject) == "" {
		return Claims{}, errors.New("invalid JWT")
	}
	return claims, nil
}

func (validator *JWTValidator) keyFunc(ctx context.Context) jwt.Keyfunc {
	return func(token *jwt.Token) (any, error) {
		kid, _ := token.Header["kid"].(string)
		if kid == "" {
			return nil, errors.New("missing JWT kid")
		}
		key, generation := validator.keyAndGeneration(kid)
		if key != nil {
			return key, nil
		}
		if err := validator.refreshUnknownKey(ctx, kid, generation); err != nil {
			return nil, err
		}
		if key := validator.key(kid); key != nil {
			return key, nil
		}
		return nil, fmt.Errorf("unknown JWT kid %q", kid)
	}
}

func (validator *JWTValidator) key(kid string) *rsa.PublicKey {
	validator.mu.RLock()
	defer validator.mu.RUnlock()
	return validator.keys[kid]
}

func (validator *JWTValidator) keyAndGeneration(kid string) (*rsa.PublicKey, uint64) {
	validator.mu.RLock()
	defer validator.mu.RUnlock()
	return validator.keys[kid], validator.generation
}

func (validator *JWTValidator) ensureKeys(ctx context.Context) error {
	now := time.Now()
	validator.mu.RLock()
	fresh := len(validator.keys) > 0 && now.Before(validator.expiresAt)
	validator.mu.RUnlock()
	if fresh {
		return nil
	}
	validator.mu.Lock()
	defer validator.mu.Unlock()
	if len(validator.keys) > 0 && time.Now().Before(validator.expiresAt) {
		return nil
	}
	keys, ttl, err := validator.fetchKeys(ctx)
	if err != nil {
		return err
	}
	validator.keys = keys
	validator.expiresAt = time.Now().Add(ttl)
	validator.generation++
	return nil
}

func (validator *JWTValidator) refreshUnknownKey(ctx context.Context, kid string, observedGeneration uint64) error {
	validator.mu.Lock()
	defer validator.mu.Unlock()
	if validator.keys[kid] != nil || validator.generation != observedGeneration {
		return nil
	}
	if time.Now().Before(validator.nextUnknownRefresh) {
		return nil
	}
	validator.nextUnknownRefresh = time.Now().Add(30 * time.Second)
	keys, ttl, err := validator.fetchKeys(ctx)
	if err != nil {
		return err
	}
	validator.keys = keys
	validator.expiresAt = time.Now().Add(ttl)
	validator.generation++
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
		return nil, 0, fmt.Errorf("JWK set returned status %d", resp.StatusCode)
	}
	raw, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return nil, 0, err
	}
	var set struct {
		Keys []struct {
			KID string `json:"kid"`
			KTY string `json:"kty"`
			Alg string `json:"alg"`
			N   string `json:"n"`
			E   string `json:"e"`
		} `json:"keys"`
	}
	if err := json.Unmarshal(raw, &set); err != nil {
		return nil, 0, err
	}
	keys := make(map[string]*rsa.PublicKey, len(set.Keys))
	for _, item := range set.Keys {
		if item.KID == "" || item.KTY != "RSA" || (item.Alg != "" && item.Alg != "RS256") {
			continue
		}
		key, err := rsaPublicKey(item.N, item.E)
		if err == nil {
			keys[item.KID] = key
		}
	}
	if len(keys) == 0 {
		return nil, 0, errors.New("JWK set did not contain RS256 keys")
	}
	return keys, cacheTTL(resp.Header.Get("Cache-Control")), nil
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
	if exponent < 3 || len(nBytes) < 256 {
		return nil, errors.New("invalid RSA JWK")
	}
	return &rsa.PublicKey{N: new(big.Int).SetBytes(nBytes), E: exponent}, nil
}

func cacheTTL(cacheControl string) time.Duration {
	for _, directive := range strings.Split(cacheControl, ",") {
		directive = strings.TrimSpace(directive)
		if strings.HasPrefix(directive, "max-age=") {
			if seconds, err := time.ParseDuration(strings.TrimPrefix(directive, "max-age=") + "s"); err == nil && seconds > 0 {
				if seconds > time.Hour {
					return time.Hour
				}
				return seconds
			}
		}
	}
	return 10 * time.Minute
}

type cachedPrincipal struct {
	principal Principal
	expiresAt time.Time
}

type principalCache struct {
	mu      sync.RWMutex
	entries map[[32]byte]cachedPrincipal
	order   [][32]byte
	next    int
	limit   int
}

func newPrincipalCache(limit int) *principalCache {
	return &principalCache{entries: make(map[[32]byte]cachedPrincipal, limit), order: make([][32]byte, 0, limit), limit: limit}
}

func (cache *principalCache) get(token [32]byte) (Principal, bool) {
	cache.mu.RLock()
	entry, ok := cache.entries[token]
	cache.mu.RUnlock()
	if !ok || !time.Now().Before(entry.expiresAt) {
		return Principal{}, false
	}
	return entry.principal, true
}

func (cache *principalCache) put(token [32]byte, principal Principal, tokenExpiry time.Time) {
	expiresAt := time.Now().Add(5 * time.Minute)
	if tokenExpiry.Before(expiresAt) {
		expiresAt = tokenExpiry
	}
	if !time.Now().Before(expiresAt) {
		return
	}
	cache.mu.Lock()
	defer cache.mu.Unlock()
	if _, exists := cache.entries[token]; exists {
		cache.entries[token] = cachedPrincipal{principal: principal, expiresAt: expiresAt}
		return
	}
	if len(cache.order) < cache.limit {
		cache.order = append(cache.order, token)
	} else {
		delete(cache.entries, cache.order[cache.next])
		cache.order[cache.next] = token
		cache.next = (cache.next + 1) % cache.limit
	}
	cache.entries[token] = cachedPrincipal{principal: principal, expiresAt: expiresAt}
}
