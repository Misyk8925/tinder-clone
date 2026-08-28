package config

import "testing"

func TestValidateRejectsBenchmarkCredentialsOutsideBenchmark(t *testing.T) {
	cfg := validConfig()
	cfg.AppEnv = "production"
	cfg.InternalAuthSecret = "secret"
	if err := cfg.Validate(); err == nil {
		t.Fatal("expected production benchmark credentials to be rejected")
	}
}

func TestValidateRequiresBenchmarkSecret(t *testing.T) {
	cfg := validConfig()
	cfg.AppEnv = "benchmark"
	if err := cfg.Validate(); err == nil {
		t.Fatal("expected benchmark mode without secret to be rejected")
	}
}

func TestValidateAllowsExplicitBenchmarkConfiguration(t *testing.T) {
	cfg := validConfig()
	cfg.AppEnv = "benchmark"
	cfg.InternalAuthSecret = "secret"
	cfg.InternalBypassProfile = true
	if err := cfg.Validate(); err != nil {
		t.Fatalf("expected valid benchmark configuration: %v", err)
	}
}

func TestValidateAllowsIsolatedInternalOnlyBenchmarkWithoutJWT(t *testing.T) {
	cfg := validConfig()
	cfg.AppEnv = "benchmark"
	cfg.InternalAuthSecret = "secret"
	cfg.InternalBypassProfile = true
	cfg.InternalOnlyBenchmark = true
	cfg.ProfileConsumersEnabled = false
	cfg.JWKSetURL = ""
	cfg.JWTIssuer = ""
	cfg.JWTAudience = ""
	if err := cfg.Validate(); err != nil {
		t.Fatalf("expected valid isolated benchmark configuration: %v", err)
	}
}

func TestValidateRejectsUnsafeInternalOnlyBenchmarkConfiguration(t *testing.T) {
	cfg := validConfig()
	cfg.AppEnv = "benchmark"
	cfg.InternalAuthSecret = "secret"
	cfg.InternalOnlyBenchmark = true
	cfg.ProfileConsumersEnabled = false
	if err := cfg.Validate(); err == nil {
		t.Fatal("expected internal-only benchmark without profile bypass to be rejected")
	}

	cfg.InternalBypassProfile = true
	cfg.ProfileConsumersEnabled = true
	if err := cfg.Validate(); err == nil {
		t.Fatal("expected internal-only benchmark with profile consumers enabled to be rejected")
	}
}

func validConfig() Config {
	return Config{
		AppEnv: "development", JWKSetURL: "http://keycloak/certs", JWTIssuer: "http://keycloak/realm",
		JWTAudience: "tinder-client", ProducerQueueCapacity: 1, ProducerConcurrency: 1,
		ProducerBatchSize: 1, ProducerBufferTimeout: 1,
	}
}
