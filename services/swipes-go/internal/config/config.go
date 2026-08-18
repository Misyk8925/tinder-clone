package config

import (
	"fmt"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	AppEnv                  string
	Port                    string
	InternalAuthSecret      string
	InternalBypassProfile   bool
	KafkaBrokers            []string
	SwipeTopic              string
	ProfileCreatedTopic     string
	ProfileDeletedTopic     string
	ProfileConsumerGroup    string
	ConsumerMaxRetries      int
	ConsumerRetryBackoff    time.Duration
	ProducerQueueCapacity   int
	ProducerConcurrency     int
	ProducerBatchSize       int
	ProducerBufferTimeout   time.Duration
	ProducerWarmupEnabled   bool
	RedisAddr               string
	DatabaseURL             string
	ProfilesBaseURL         string
	JWKSetURL               string
	JWTIssuer               string
	JWTAudience             string
	ProfileConsumersEnabled bool
}

func Load() (Config, error) {
	cfg := Config{
		AppEnv:                  strings.ToLower(stringEnv("development", "APP_ENV")),
		Port:                    stringEnv("8040", "PORT", "SERVER_PORT"),
		InternalAuthSecret:      firstEnv("INTERNAL_SWIPES_AUTH_SECRET", "SWIPES_INTERNAL_AUTH_SECRET"),
		InternalBypassProfile:   boolEnv(false, "SWIPES_INTERNAL_BYPASS_PROFILE_CHECK"),
		KafkaBrokers:            splitCSV(firstEnv("KAFKA_BROKERS", "SPRING_KAFKA_BOOTSTRAP_SERVERS")),
		SwipeTopic:              stringEnv("swipe-created", "SWIPES_KAFKA_TOPIC", "KAFKA_TOPIC_SWIPE_CREATED"),
		ProfileCreatedTopic:     stringEnv("profile.created", "KAFKA_TOPICS_PROFILE_CREATED", "KAFKA_TOPIC_PROFILE_CREATED"),
		ProfileDeletedTopic:     stringEnv("profile.deleted", "KAFKA_TOPICS_PROFILE_DELETED", "KAFKA_TOPIC_PROFILE_DELETED"),
		ProfileConsumerGroup:    stringEnv("swipes-profile-cache", "SWIPES_PROFILE_CONSUMER_GROUP"),
		ConsumerMaxRetries:      intEnv(5, "SWIPES_KAFKA_CONSUMER_MAX_RETRIES"),
		ConsumerRetryBackoff:    durationEnv(time.Second, "SWIPES_KAFKA_CONSUMER_RETRY_BACKOFF"),
		ProducerQueueCapacity:   intEnv(200000, "SWIPES_PRODUCER_QUEUE_CAPACITY"),
		ProducerConcurrency:     intEnv(4, "SWIPES_PRODUCER_CONCURRENCY", "SWIPES_PRODUCER_WORKER_COUNT"),
		ProducerBatchSize:       intEnv(500, "SWIPES_PRODUCER_BATCH_SIZE"),
		ProducerBufferTimeout:   durationEnv(time.Millisecond, "SWIPES_PRODUCER_BUFFER_TIMEOUT"),
		ProducerWarmupEnabled:   boolEnv(true, "SWIPES_PRODUCER_WARMUP_ENABLED"),
		RedisAddr:               redisAddrFromEnv(),
		DatabaseURL:             databaseURLFromEnv(),
		ProfilesBaseURL:         strings.TrimRight(stringEnv("http://localhost:8010/api/v1/profiles", "SERVICES_PROFILES_BASE_URL", "PROFILES_BASE_URL"), "/"),
		JWKSetURL:               firstEnv("JWK_SET_URL", "SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI", "KEYCLOAK_JWK_SET_URI"),
		JWTIssuer:               firstEnv("SWIPES_JWT_ISSUER", "JWT_ISSUER"),
		JWTAudience:             stringEnv("tinder-client", "SWIPES_JWT_AUDIENCE", "JWT_AUDIENCE"),
		ProfileConsumersEnabled: boolEnv(true, "PROFILE_CACHE_CONSUMERS_ENABLED", "SWIPES_PROFILE_CACHE_CONSUMERS_ENABLED"),
	}
	if len(cfg.KafkaBrokers) == 0 {
		cfg.KafkaBrokers = []string{"localhost:9092"}
	}
	if cfg.JWTIssuer == "" {
		cfg.JWTIssuer = issuerFromJWKURL(cfg.JWKSetURL)
	}
	if err := cfg.Validate(); err != nil {
		return Config{}, err
	}
	return cfg, nil
}

func (cfg Config) Validate() error {
	if cfg.ProducerQueueCapacity < 1 || cfg.ProducerConcurrency < 1 || cfg.ProducerBatchSize < 1 {
		return fmt.Errorf("producer queue capacity, concurrency, and batch size must be positive")
	}
	if cfg.ProducerBufferTimeout <= 0 {
		return fmt.Errorf("producer buffer timeout must be positive")
	}
	if cfg.ConsumerMaxRetries < 0 || cfg.ConsumerRetryBackoff < 0 {
		return fmt.Errorf("consumer retries and retry backoff must not be negative")
	}
	if cfg.JWKSetURL == "" || cfg.JWTIssuer == "" || cfg.JWTAudience == "" {
		return fmt.Errorf("JWT JWK URL, issuer, and audience are required")
	}
	benchmarkAuth := cfg.InternalAuthSecret != "" || cfg.InternalBypassProfile
	if benchmarkAuth && cfg.AppEnv != "benchmark" {
		return fmt.Errorf("internal swipe authentication is allowed only when APP_ENV=benchmark")
	}
	if cfg.AppEnv == "benchmark" && cfg.InternalAuthSecret == "" {
		return fmt.Errorf("INTERNAL_SWIPES_AUTH_SECRET is required in benchmark mode")
	}
	return nil
}

func firstEnv(keys ...string) string {
	for _, key := range keys {
		if value, ok := os.LookupEnv(key); ok {
			return strings.TrimSpace(value)
		}
	}
	return ""
}

func stringEnv(def string, keys ...string) string {
	if value := firstEnv(keys...); value != "" {
		return value
	}
	return def
}

func intEnv(def int, keys ...string) int {
	if value := firstEnv(keys...); value != "" {
		if parsed, err := strconv.Atoi(value); err == nil {
			return parsed
		}
	}
	return def
}

func boolEnv(def bool, keys ...string) bool {
	if value := firstEnv(keys...); value != "" {
		if parsed, err := strconv.ParseBool(value); err == nil {
			return parsed
		}
	}
	return def
}

func durationEnv(def time.Duration, keys ...string) time.Duration {
	if value := firstEnv(keys...); value != "" {
		if parsed, err := time.ParseDuration(value); err == nil {
			return parsed
		}
	}
	return def
}

func splitCSV(value string) []string {
	parts := strings.Split(value, ",")
	result := make([]string, 0, len(parts))
	for _, part := range parts {
		if trimmed := strings.TrimSpace(part); trimmed != "" {
			result = append(result, trimmed)
		}
	}
	return result
}

func redisAddrFromEnv() string {
	if value := firstEnv("REDIS_ADDR", "SPRING_REDIS_URL"); value != "" {
		return value
	}
	return stringEnv("localhost", "SPRING_DATA_REDIS_HOST", "REDIS_HOST") + ":" + stringEnv("6379", "SPRING_DATA_REDIS_PORT", "REDIS_PORT")
}

func databaseURLFromEnv() string {
	if value := firstEnv("DATABASE_URL", "POSTGRES_DSN"); value != "" {
		return value
	}
	raw := firstEnv("SPRING_DATASOURCE_URL", "SWIPES_DB_URL")
	if raw == "" {
		return ""
	}
	converted, _ := jdbcPostgresToURL(raw, stringEnv("swipes_app", "SPRING_DATASOURCE_USERNAME", "SWIPES_DB_USER"), firstEnv("SPRING_DATASOURCE_PASSWORD", "SWIPES_DB_PASSWORD"))
	return converted
}

func jdbcPostgresToURL(raw, user, password string) (string, error) {
	const prefix = "jdbc:postgresql://"
	if !strings.HasPrefix(raw, prefix) {
		return raw, nil
	}
	parsed, err := url.Parse("postgres://" + strings.TrimPrefix(raw, prefix))
	if err != nil || parsed.Host == "" || parsed.Path == "" {
		return "", fmt.Errorf("invalid JDBC PostgreSQL URL")
	}
	if parsed.Query().Get("sslmode") == "" {
		query := parsed.Query()
		query.Set("sslmode", "disable")
		parsed.RawQuery = query.Encode()
	}
	if user != "" {
		parsed.User = url.UserPassword(user, password)
	}
	return parsed.String(), nil
}

func issuerFromJWKURL(raw string) string {
	return strings.TrimSuffix(strings.TrimRight(raw, "/"), "/protocol/openid-connect/certs")
}
