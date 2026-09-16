package main

import (
	"crypto"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"math/big"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func issuer(t *testing.T) *Issuer {
	t.Helper()
	signer, err := NewIssuer("http://bench/realms/bench", "tinder-client", time.Hour)
	if err != nil {
		t.Fatalf("new issuer: %v", err)
	}
	return signer
}

// Given a minted token, when it is verified with the public key published in the JWKS,
// then the signature holds. This is the whole premise of slice 4b: if a service cannot
// verify these tokens from the JWKS alone, minting them locally buys nothing.
func TestTokenVerifiesAgainstThePublishedJWKS(t *testing.T) {
	signer := issuer(t)

	token, err := signer.Token("viewer-1")
	if err != nil {
		t.Fatalf("mint: %v", err)
	}
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		t.Fatalf("token has %d segments, want 3", len(parts))
	}

	publicKey := publicKeyFromJWKS(t, signer)
	digest := sha256.Sum256([]byte(parts[0] + "." + parts[1]))
	signature := decodeSegment(t, parts[2])

	if err := rsa.VerifyPKCS1v15(publicKey, crypto.SHA256, digest[:], signature); err != nil {
		t.Fatalf("a service could not verify this token: %v", err)
	}
}

// Given a minted token, when its claims are read, then the subject is the viewer — that is
// what deck-read resolves the viewer from.
func TestTokenCarriesTheViewerAsSubject(t *testing.T) {
	signer := issuer(t)

	token, err := signer.Token("viewer-42")
	if err != nil {
		t.Fatalf("mint: %v", err)
	}
	claims := map[string]any{}
	if err := json.Unmarshal(decodeSegment(t, strings.Split(token, ".")[1]), &claims); err != nil {
		t.Fatalf("decode claims: %v", err)
	}

	if claims["sub"] != "viewer-42" {
		t.Fatalf("sub is %v, want viewer-42", claims["sub"])
	}
	if claims["iss"] != "http://bench/realms/bench" {
		t.Fatalf("iss is %v; a service validating the issuer strictly would reject it", claims["iss"])
	}
	if claims["aud"] != "tinder-client" {
		t.Fatalf("aud is %v, want tinder-client", claims["aud"])
	}
	if claims["exp"] == nil || claims["iat"] == nil {
		t.Fatal("token has no lifetime")
	}
}

// Given a header, when its kid is compared with the JWKS, then they match — a service
// picks the key by kid.
func TestTokenHeaderNamesTheKeyInTheJWKS(t *testing.T) {
	signer := issuer(t)

	token, err := signer.Token("viewer-1")
	if err != nil {
		t.Fatalf("mint: %v", err)
	}
	header := map[string]any{}
	if err := json.Unmarshal(decodeSegment(t, strings.Split(token, ".")[0]), &header); err != nil {
		t.Fatalf("decode header: %v", err)
	}

	if header["alg"] != "RS256" || header["kid"] != signer.KeyID() {
		t.Fatalf("header %v does not name the published key %s", header, signer.KeyID())
	}
}

// Given a key file, when the issuer is loaded twice, then the key id is stable. A fresh key
// per run would invalidate the JWKS a service was configured with.
func TestSigningKeySurvivesARestart(t *testing.T) {
	path := filepath.Join(t.TempDir(), "signing-key.pem")

	first, err := LoadOrCreateIssuer(path, "http://bench/realms/bench", "tinder-client", time.Hour)
	if err != nil {
		t.Fatalf("create: %v", err)
	}
	second, err := LoadOrCreateIssuer(path, "http://bench/realms/bench", "tinder-client", time.Hour)
	if err != nil {
		t.Fatalf("reload: %v", err)
	}

	if first.KeyID() != second.KeyID() {
		t.Fatalf("key id changed across restart: %s then %s", first.KeyID(), second.KeyID())
	}
}

func publicKeyFromJWKS(t *testing.T, signer *Issuer) *rsa.PublicKey {
	t.Helper()
	document, err := signer.JWKS()
	if err != nil {
		t.Fatalf("jwks: %v", err)
	}
	var parsed struct {
		Keys []struct {
			Kid string `json:"kid"`
			N   string `json:"n"`
			E   string `json:"e"`
		} `json:"keys"`
	}
	if err := json.Unmarshal(document, &parsed); err != nil {
		t.Fatalf("decode jwks: %v", err)
	}
	if len(parsed.Keys) != 1 {
		t.Fatalf("jwks holds %d keys, want 1", len(parsed.Keys))
	}

	modulus := new(big.Int).SetBytes(decodeSegment(t, parsed.Keys[0].N))
	exponent := new(big.Int).SetBytes(decodeSegment(t, parsed.Keys[0].E))
	return &rsa.PublicKey{N: modulus, E: int(exponent.Int64())}
}

func decodeSegment(t *testing.T, segment string) []byte {
	t.Helper()
	decoded, err := base64.RawURLEncoding.DecodeString(segment)
	if err != nil {
		t.Fatalf("decode segment: %v", err)
	}
	return decoded
}
