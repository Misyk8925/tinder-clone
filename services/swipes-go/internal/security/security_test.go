package security

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"math/big"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func TestValidateEnforcesClaimsAndExtractsRoles(t *testing.T) {
	validator, token := validatorFixture(t, "issuer", "audience")
	claims, err := validator.Validate(context.Background(), token)
	if err != nil {
		t.Fatalf("expected valid token: %v", err)
	}
	if claims.Subject != "user-1" {
		t.Fatalf("unexpected subject: %s", claims.Subject)
	}
	auth := NewAuthenticator("", validator)
	principal, err := auth.Authenticate(context.Background(), []byte("Bearer "+token), nil)
	if err != nil || !principal.HasAnyRole("USER_PREMIUM") {
		t.Fatalf("expected premium principal: %+v %v", principal, err)
	}
}

func TestValidateRejectsWrongAudience(t *testing.T) {
	validator, token := validatorFixture(t, "issuer", "other-audience")
	if _, err := validator.Validate(context.Background(), token); err == nil {
		t.Fatal("expected audience mismatch")
	}
}

func TestAuthenticatorAcceptsOnlyExactBenchmarkSecret(t *testing.T) {
	auth := NewAuthenticator("benchmark-secret", nil)
	principal, err := auth.Authenticate(context.Background(), nil, []byte("benchmark-secret"))
	if err != nil || !principal.Benchmark {
		t.Fatalf("expected benchmark principal: %+v %v", principal, err)
	}
	if _, err := auth.Authenticate(context.Background(), nil, []byte("benchmark-secreu")); err == nil {
		t.Fatal("expected invalid secret to fall through to JWT and fail")
	}
}

func BenchmarkJWTValidate(b *testing.B) {
	validator, token := validatorFixture(b, "issuer", "audience")
	b.ReportAllocs()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		if _, err := validator.Validate(context.Background(), token); err != nil {
			b.Fatal(err)
		}
	}
}

func BenchmarkAuthenticateCached(b *testing.B) {
	validator, token := validatorFixture(b, "issuer", "audience")
	auth := NewAuthenticator("", validator)
	header := []byte("Bearer " + token)
	if _, err := auth.Authenticate(context.Background(), header, nil); err != nil {
		b.Fatal(err)
	}
	b.ReportAllocs()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		if _, err := auth.Authenticate(context.Background(), header, nil); err != nil {
			b.Fatal(err)
		}
	}
}

type testingTB interface {
	Helper()
	Fatalf(string, ...any)
	Cleanup(func())
}

func validatorFixture(tb testingTB, expectedIssuer, tokenAudience string) (*JWTValidator, string) {
	tb.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		tb.Fatalf("RSA key: %v", err)
	}
	kid := "test-key"
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set("Cache-Control", "max-age=60")
		_ = json.NewEncoder(response).Encode(map[string]any{"keys": []any{map[string]any{
			"kid": kid, "kty": "RSA", "alg": "RS256", "n": base64.RawURLEncoding.EncodeToString(key.N.Bytes()),
			"e": base64.RawURLEncoding.EncodeToString(big.NewInt(int64(key.E)).Bytes()),
		}}})
	}))
	tb.Cleanup(server.Close)
	validator := NewJWTValidator(server.URL, expectedIssuer, "audience")
	if err := validator.Initialize(context.Background()); err != nil {
		tb.Fatalf("initialize validator: %v", err)
	}
	claims := Claims{
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer: "issuer", Subject: "user-1", Audience: jwt.ClaimStrings{tokenAudience},
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(time.Hour)),
		},
		RealmAccess: keycloakAccess{Roles: []string{"USER_PREMIUM"}},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	token.Header["kid"] = kid
	signed, err := token.SignedString(key)
	if err != nil {
		tb.Fatalf("sign token: %v", err)
	}
	return validator, signed
}
