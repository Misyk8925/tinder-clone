package main

import (
	"fmt"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	Port                    string
	HTTPServerEngine        string
	InternalAuthSecret      string
	InternalBypassProfile   bool
	KafkaBrokers            []string
	SwipeTopic              string
	ProfileCreatedTopic     string
	ProfileDeletedTopic     string
	ProducerQueueCapacity   int
	ProducerConcurrency     int
	ProducerBatchSize       int
	ProducerBufferTimeout   time.Duration
	ProducerWarmupEnabled   bool
	RedisAddr               string
	DatabaseURL             string
	ProfilesBaseURL         string
	JWKSetURL               string
	ProfileConsumersEnabled bool
}

func LoadConfig() (Config, error) {
	cfg := Config{
		Port:                    firstEnv("PORT", "SERVER_PORT"),
		HTTPServerEngine:        strings.ToLower(stringEnv("nethttp", "HTTP_SERVER_ENGINE")),
		InternalAuthSecret:      firstEnv("INTERNAL_SWIPES_AUTH_SECRET", "SWIPES_INTERNAL_AUTH_SECRET"),
		InternalBypassProfile:   boolEnv(false, "SWIPES_INTERNAL_BYPASS_PROFILE_CHECK"),
		KafkaBrokers:            splitCSV(firstEnv("KAFKA_BROKERS", "SPRING_KAFKA_BOOTSTRAP_SERVERS")),
		SwipeTopic:              stringEnv("swipe-created", "SWIPES_KAFKA_TOPIC", "KAFKA_TOPIC_SWIPE_CREATED"),
		ProfileCreatedTopic:     stringEnv("profile.created", "KAFKA_TOPICS_PROFILE_CREATED", "KAFKA_TOPIC_PROFILE_CREATED"),
		ProfileDeletedTopic:     stringEnv("profile.deleted", "KAFKA_TOPICS_PROFILE_DELETED", "KAFKA_TOPIC_PROFILE_DELETED"),
		ProducerQueueCapacity:   intEnv(200000, "SWIPES_PRODUCER_QUEUE_CAPACITY"),
		ProducerConcurrency:     intEnv(4, "SWIPES_PRODUCER_CONCURRENCY", "SWIPES_PRODUCER_WORKER_COUNT"),
		ProducerBatchSize:       intEnv(500, "SWIPES_PRODUCER_BATCH_SIZE"),
		ProducerBufferTimeout:   durationEnv(time.Millisecond, "SWIPES_PRODUCER_BUFFER_TIMEOUT"),
		ProducerWarmupEnabled:   boolEnv(true, "SWIPES_PRODUCER_WARMUP_ENABLED"),
		RedisAddr:               redisAddrFromEnv(),
		DatabaseURL:             databaseURLFromEnv(),
		ProfilesBaseURL:         strings.TrimRight(stringEnv("http://localhost:8010/api/v1/profiles", "SERVICES_PROFILES_BASE_URL", "PROFILES_BASE_URL"), "/"),
		JWKSetURL:               firstEnv("JWK_SET_URL", "SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI", "KEYCLOAK_JWK_SET_URI"),
		ProfileConsumersEnabled: boolEnv(true, "PROFILE_CACHE_CONSUMERS_ENABLED", "SWIPES_PROFILE_CACHE_CONSUMERS_ENABLED"),
	}
	if cfg.Port == "" {
		cfg.Port = "8040"
	}
	if len(cfg.KafkaBrokers) == 0 {
		cfg.KafkaBrokers = []string{"localhost:9092"}
	}
	if cfg.ProducerQueueCapacity < 1 {
		cfg.ProducerQueueCapacity = 1
	}
	if cfg.ProducerConcurrency < 1 {
		cfg.ProducerConcurrency = 1
	}
	if cfg.ProducerBatchSize < 1 {
		cfg.ProducerBatchSize = 1
	}
	if cfg.ProducerBufferTimeout <= 0 {
		cfg.ProducerBufferTimeout = time.Millisecond
	}
	return cfg, nil
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
		parsed, err := strconv.Atoi(value)
		if err == nil {
			return parsed
		}
	}
	return def
}

func boolEnv(def bool, keys ...string) bool {
	if value := firstEnv(keys...); value != "" {
		parsed, err := strconv.ParseBool(value)
		if err == nil {
			return parsed
		}
	}
	return def
}

func durationEnv(def time.Duration, keys ...string) time.Duration {
	if value := firstEnv(keys...); value != "" {
		parsed, err := time.ParseDuration(value)
		if err == nil {
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
	host := stringEnv("localhost", "SPRING_DATA_REDIS_HOST", "REDIS_HOST")
	port := stringEnv("6379", "SPRING_DATA_REDIS_PORT", "REDIS_PORT")
	return host + ":" + port
}

func databaseURLFromEnv() string {
	if value := firstEnv("DATABASE_URL", "POSTGRES_DSN"); value != "" {
		return value
	}
	raw := firstEnv("SPRING_DATASOURCE_URL", "SWIPES_DB_URL")
	if raw == "" {
		return ""
	}
	user := stringEnv("swipes_app", "SPRING_DATASOURCE_USERNAME", "SWIPES_DB_USER")
	password := firstEnv("SPRING_DATASOURCE_PASSWORD", "SWIPES_DB_PASSWORD")
	converted, err := jdbcPostgresToURL(raw, user, password)
	if err != nil {
		return ""
	}
	return converted
}

func jdbcPostgresToURL(raw, user, password string) (string, error) {
	const prefix = "jdbc:postgresql://"
	if !strings.HasPrefix(raw, prefix) {
		return raw, nil
	}
	withoutPrefix := strings.TrimPrefix(raw, prefix)
	parts := strings.SplitN(withoutPrefix, "?", 2)
	hostPath := parts[0]
	query := url.Values{}
	if len(parts) == 2 {
		parsedQuery, err := url.ParseQuery(parts[1])
		if err == nil {
			query = parsedQuery
		}
	}
	if query.Get("sslmode") == "" {
		query.Set("sslmode", "disable")
	}
	u := url.URL{
		Scheme:   "postgres",
		Host:     strings.SplitN(hostPath, "/", 2)[0],
		RawQuery: query.Encode(),
	}
	if strings.Contains(hostPath, "/") {
		u.Path = "/" + strings.SplitN(hostPath, "/", 2)[1]
	}
	if user != "" {
		if password != "" {
			u.User = url.UserPassword(user, password)
		} else {
			u.User = url.User(user)
		}
	}
	if u.Host == "" || u.Path == "" {
		return "", fmt.Errorf("invalid jdbc postgres url")
	}
	return u.String(), nil
}
