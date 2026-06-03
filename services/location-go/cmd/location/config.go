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
	Port               string
	DatabaseURL        string
	GeocodingBaseURL   string
	GeocodingUserAgent string
	GeocodingCountries string
	GeocodingTimeoutMs int
}

func LoadConfig() Config {
	return Config{
		Port:               stringEnv("8065", "PORT"),
		DatabaseURL:        databaseURLFromEnv(),
		GeocodingBaseURL:   stringEnv("https://nominatim.openstreetmap.org", "GEOCODING_BASE_URL"),
		GeocodingUserAgent: stringEnv("tinder-clone-app", "GEOCODING_USER_AGENT"),
		GeocodingCountries: stringEnv("", "GEOCODING_COUNTRY_CODES"),
		GeocodingTimeoutMs: intEnv(3000, "GEOCODING_TIMEOUT_MS"),
	}
}

func (c Config) GeocodingTimeout() time.Duration {
	return time.Duration(c.GeocodingTimeoutMs) * time.Millisecond
}

func stringEnv(def string, keys ...string) string {
	for _, key := range keys {
		if v, ok := os.LookupEnv(key); ok && strings.TrimSpace(v) != "" {
			return strings.TrimSpace(v)
		}
	}
	return def
}

func intEnv(def int, keys ...string) int {
	for _, key := range keys {
		if v, ok := os.LookupEnv(key); ok {
			if n, err := strconv.Atoi(strings.TrimSpace(v)); err == nil {
				return n
			}
		}
	}
	return def
}

func databaseURLFromEnv() string {
	if v := stringEnv("", "DATABASE_URL", "POSTGRES_DSN"); v != "" {
		return v
	}
	raw := stringEnv("", "SPRING_DATASOURCE_URL", "LOCATION_DB_URL")
	if raw == "" {
		return ""
	}
	user := stringEnv("location_app", "SPRING_DATASOURCE_USERNAME", "LOCATION_DB_USER")
	password := stringEnv("", "SPRING_DATASOURCE_PASSWORD", "LOCATION_DB_PASSWORD")
	converted, err := jdbcToPostgresURL(raw, user, password)
	if err != nil {
		return raw
	}
	return converted
}

func jdbcToPostgresURL(raw, user, password string) (string, error) {
	const prefix = "jdbc:postgresql://"
	if !strings.HasPrefix(raw, prefix) {
		return raw, nil
	}
	withoutPrefix := strings.TrimPrefix(raw, prefix)
	parts := strings.SplitN(withoutPrefix, "?", 2)
	hostPath := parts[0]
	query := url.Values{}
	if len(parts) == 2 {
		if parsed, err := url.ParseQuery(parts[1]); err == nil {
			query = parsed
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
		return "", fmt.Errorf("invalid jdbc postgres url: %s", raw)
	}
	return u.String(), nil
}
