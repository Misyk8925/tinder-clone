package main

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"fmt"
	"math/big"
	"os"
	"strings"
	"time"
)

// Issuer mints viewer tokens locally instead of asking Keycloak for one per agent.
//
// Both services that matter here validate against a configurable issuer and JWKS —
// `quarkus.oidc.token.issuer` in deck-read, `JWK_SET_URL` / `JWT_ISSUER` in swipes-go — so
// pointing a bench deployment at this issuer is configuration, not a code change. That
// keeps a hundred thousand viewers from becoming a hundred thousand Keycloak users.
//
// This belongs to a disposable bench and nowhere else: whoever holds this key can sign a
// token for any subject.
type Issuer struct {
	key      *rsa.PrivateKey
	keyID    string
	issuer   string
	audience string
	lifetime time.Duration
}

func NewIssuer(issuer, audience string, lifetime time.Duration) (*Issuer, error) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return nil, fmt.Errorf("generate signing key: %w", err)
	}
	return newIssuerFromKey(key, issuer, audience, lifetime), nil
}

// Token signs an access token for one viewer. The subject is what deck-read resolves the
// viewer from, so it has to be the agent's own id.
func (i *Issuer) Token(subject string, roles ...string) (string, error) {
	if len(roles) == 0 {
		roles = []string{"USER_BASIC"}
	}
	now := time.Now()

	header := map[string]any{"alg": "RS256", "typ": "JWT", "kid": i.keyID}
	claims := map[string]any{
		"iss":                i.issuer,
		"sub":                subject,
		"aud":                i.audience,
		"azp":                i.audience,
		"exp":                now.Add(i.lifetime).Unix(),
		"iat":                now.Unix(),
		"typ":                "Bearer",
		"preferred_username": "sim-" + subject,
		"realm_access":       map[string]any{"roles": roles},
	}

	encodedHeader, err := encodeSegment(header)
	if err != nil {
		return "", err
	}
	encodedClaims, err := encodeSegment(claims)
	if err != nil {
		return "", err
	}

	signingInput := encodedHeader + "." + encodedClaims
	digest := sha256.Sum256([]byte(signingInput))
	signature, err := rsa.SignPKCS1v15(rand.Reader, i.key, crypto.SHA256, digest[:])
	if err != nil {
		return "", fmt.Errorf("sign token: %w", err)
	}
	return signingInput + "." + base64URL(signature), nil
}

// JWKS is the document a service fetches to verify these tokens.
func (i *Issuer) JWKS() ([]byte, error) {
	document := map[string]any{"keys": []map[string]any{{
		"kty": "RSA",
		"use": "sig",
		"alg": "RS256",
		"kid": i.keyID,
		"n":   base64URL(i.key.PublicKey.N.Bytes()),
		"e":   base64URL(big.NewInt(int64(i.key.PublicKey.E)).Bytes()),
	}}}
	return json.Marshal(document)
}

func (i *Issuer) KeyID() string { return i.keyID }

func encodeSegment(value map[string]any) (string, error) {
	encoded, err := json.Marshal(value)
	if err != nil {
		return "", fmt.Errorf("encode token segment: %w", err)
	}
	return base64URL(encoded), nil
}

func base64URL(value []byte) string {
	return strings.TrimRight(base64.URLEncoding.EncodeToString(value), "=")
}

// LoadOrCreateIssuer keeps the signing key on disk so the JWKS a service was configured
// with stays valid across runs. A fresh key every run would invalidate every token the
// moment the driver restarted.
func LoadOrCreateIssuer(keyPath, issuer, audience string, lifetime time.Duration) (*Issuer, error) {
	encoded, err := os.ReadFile(keyPath)
	if err == nil {
		block, _ := pem.Decode(encoded)
		if block == nil {
			return nil, fmt.Errorf("%s is not a PEM file", keyPath)
		}
		key, parseErr := x509.ParsePKCS1PrivateKey(block.Bytes)
		if parseErr != nil {
			return nil, fmt.Errorf("parse %s: %w", keyPath, parseErr)
		}
		return newIssuerFromKey(key, issuer, audience, lifetime), nil
	}
	if !os.IsNotExist(err) {
		return nil, fmt.Errorf("read %s: %w", keyPath, err)
	}

	created, err := NewIssuer(issuer, audience, lifetime)
	if err != nil {
		return nil, err
	}
	block := &pem.Block{Type: "RSA PRIVATE KEY", Bytes: x509.MarshalPKCS1PrivateKey(created.key)}
	// 0600: whoever holds this key can sign a token for any subject.
	if err := os.WriteFile(keyPath, pem.EncodeToMemory(block), 0o600); err != nil {
		return nil, fmt.Errorf("write %s: %w", keyPath, err)
	}
	return created, nil
}

func newIssuerFromKey(key *rsa.PrivateKey, issuer, audience string, lifetime time.Duration) *Issuer {
	fingerprint := sha256.Sum256(x509.MarshalPKCS1PublicKey(&key.PublicKey))
	return &Issuer{
		key:      key,
		keyID:    base64URL(fingerprint[:8]),
		issuer:   issuer,
		audience: audience,
		lifetime: lifetime,
	}
}
