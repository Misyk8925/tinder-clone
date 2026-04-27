package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
	"github.com/segmentio/kafka-go"
)

const profileExistsSetKey = "profiles:exists"

type ProfileCache struct {
	redis           *redis.Client
	db              *pgxpool.Pool
	profilesBaseURL string
	httpClient      *http.Client
	log             *log.Logger
}

func NewProfileCache(ctx context.Context, cfg Config, logger *log.Logger) *ProfileCache {
	cache := &ProfileCache{
		profilesBaseURL: strings.TrimRight(cfg.ProfilesBaseURL, "/"),
		httpClient:      &http.Client{Timeout: 3 * time.Second},
		log:             logger,
	}

	if cfg.RedisAddr != "" {
		client := redis.NewClient(&redis.Options{Addr: cfg.RedisAddr})
		pingCtx, cancel := context.WithTimeout(ctx, 500*time.Millisecond)
		if err := client.Ping(pingCtx).Err(); err != nil {
			logger.Printf("redis cache disabled: %v", err)
			_ = client.Close()
		} else {
			cache.redis = client
		}
		cancel()
	}

	if cfg.DatabaseURL != "" {
		poolCtx, cancel := context.WithTimeout(ctx, 3*time.Second)
		pool, err := pgxpool.New(poolCtx, cfg.DatabaseURL)
		if err != nil {
			logger.Printf("postgres profile cache disabled: %v", err)
		} else if err := pool.Ping(poolCtx); err != nil {
			logger.Printf("postgres profile cache disabled: %v", err)
			pool.Close()
		} else if err := ensureProfileSchema(poolCtx, pool); err != nil {
			logger.Printf("postgres profile cache disabled: %v", err)
			pool.Close()
		} else {
			cache.db = pool
		}
		cancel()
	}

	return cache
}

func (cache *ProfileCache) Close() {
	if cache.redis != nil {
		_ = cache.redis.Close()
	}
	if cache.db != nil {
		cache.db.Close()
	}
}

func ensureProfileSchema(ctx context.Context, pool *pgxpool.Pool) error {
	_, err := pool.Exec(ctx, `
CREATE TABLE IF NOT EXISTS profile_cache (
  profile_id uuid PRIMARY KEY,
  user_id text NOT NULL,
  created_at timestamptz NOT NULL
)`)
	return err
}

func (cache *ProfileCache) ExistsAll(ctx context.Context, profile1ID, profile2ID uuid.UUID, bearerToken string) (bool, error) {
	if profile1ID == profile2ID {
		return false, nil
	}

	found := map[uuid.UUID]bool{}
	if cache.redis != nil {
		if cache.isInRedis(ctx, profile1ID) {
			found[profile1ID] = true
		}
		if cache.isInRedis(ctx, profile2ID) {
			found[profile2ID] = true
		}
		if len(found) == 2 {
			return true, nil
		}
	}

	missing := missingUUIDs(found, profile1ID, profile2ID)
	if len(missing) > 0 && cache.db != nil {
		dbFound := cache.checkInDB(ctx, missing)
		for _, id := range dbFound {
			found[id] = true
		}
		cache.warmRedis(ctx, dbFound)
		if len(found) == 2 {
			return true, nil
		}
	}

	missing = missingUUIDs(found, profile1ID, profile2ID)
	if len(missing) == 0 {
		return true, nil
	}
	confirmed, err := cache.findExistingRemote(ctx, missing, bearerToken)
	if err != nil {
		cache.log.Printf("profiles fallback failed: %v", err)
		return false, nil
	}
	for _, id := range confirmed {
		found[id] = true
	}
	if len(confirmed) > 0 {
		cache.populateCache(ctx, confirmed)
	}
	return len(found) == 2, nil
}

func missingUUIDs(found map[uuid.UUID]bool, ids ...uuid.UUID) []uuid.UUID {
	missing := make([]uuid.UUID, 0, len(ids))
	for _, id := range ids {
		if !found[id] {
			missing = append(missing, id)
		}
	}
	return missing
}

func (cache *ProfileCache) isInRedis(ctx context.Context, profileID uuid.UUID) bool {
	redisCtx, cancel := context.WithTimeout(ctx, 250*time.Millisecond)
	defer cancel()
	ok, err := cache.redis.SIsMember(redisCtx, profileExistsSetKey, profileID.String()).Result()
	return err == nil && ok
}

func (cache *ProfileCache) checkInDB(ctx context.Context, ids []uuid.UUID) []uuid.UUID {
	if len(ids) == 0 || cache.db == nil {
		return nil
	}
	dbCtx, cancel := context.WithTimeout(ctx, 750*time.Millisecond)
	defer cancel()
	rows, err := cache.db.Query(dbCtx,
		`SELECT profile_id FROM profile_cache WHERE profile_id = $1 OR profile_id = $2`,
		ids[0], secondUUID(ids))
	if err != nil {
		cache.log.Printf("profile cache db lookup failed: %v", err)
		return nil
	}
	defer rows.Close()
	found := make([]uuid.UUID, 0, len(ids))
	for rows.Next() {
		var id uuid.UUID
		if err := rows.Scan(&id); err == nil {
			found = append(found, id)
		}
	}
	return found
}

func secondUUID(ids []uuid.UUID) uuid.UUID {
	if len(ids) > 1 {
		return ids[1]
	}
	return ids[0]
}

func (cache *ProfileCache) warmRedis(ctx context.Context, ids []uuid.UUID) {
	if cache.redis == nil || len(ids) == 0 {
		return
	}
	members := make([]interface{}, len(ids))
	for i, id := range ids {
		members[i] = id.String()
	}
	redisCtx, cancel := context.WithTimeout(ctx, 250*time.Millisecond)
	defer cancel()
	if err := cache.redis.SAdd(redisCtx, profileExistsSetKey, members...).Err(); err != nil {
		cache.log.Printf("failed to warm redis for %d profile(s): %v", len(ids), err)
	}
}

func (cache *ProfileCache) findExistingRemote(ctx context.Context, ids []uuid.UUID, bearerToken string) ([]uuid.UUID, error) {
	if cache.profilesBaseURL == "" || len(ids) == 0 {
		return nil, nil
	}
	parts := make([]string, len(ids))
	for i, id := range ids {
		parts[i] = id.String()
	}
	endpoint := cache.profilesBaseURL + "/by-ids?ids=" + url.QueryEscape(strings.Join(parts, ","))
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, err
	}
	if bearerToken != "" {
		req.Header.Set("Authorization", "Bearer "+bearerToken)
	}
	resp, err := cache.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("profiles service returned status %d", resp.StatusCode)
	}
	var response []struct {
		ProfileID uuid.UUID `json:"profileId"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return nil, err
	}
	found := make([]uuid.UUID, 0, len(response))
	for _, item := range response {
		if item.ProfileID != uuid.Nil {
			found = append(found, item.ProfileID)
		}
	}
	return found, nil
}

func (cache *ProfileCache) populateCache(ctx context.Context, ids []uuid.UUID) {
	if cache.db != nil {
		dbCtx, cancel := context.WithTimeout(ctx, time.Second)
		defer cancel()
		for _, id := range ids {
			_, err := cache.db.Exec(dbCtx, `
INSERT INTO profile_cache (profile_id, user_id, created_at)
VALUES ($1, 'unknown', now())
ON CONFLICT (profile_id) DO NOTHING`, id)
			if err != nil {
				cache.log.Printf("failed to populate profile cache id=%s: %v", id, err)
			}
		}
	}
	cache.warmRedis(ctx, ids)
}

func (cache *ProfileCache) SaveProfile(ctx context.Context, event ProfileCreateEvent) {
	if event.ProfileID == "" {
		cache.log.Printf("skipping profile create event with empty profileId")
		return
	}
	profileID, err := uuid.Parse(event.ProfileID)
	if err != nil {
		cache.log.Printf("skipping profile create event with invalid profileId=%q", event.ProfileID)
		return
	}
	createdAt := time.Now()
	if event.Timestamp != nil {
		createdAt = *event.Timestamp
	}
	userID := "unknown"
	if event.UserID != nil {
		userID = *event.UserID
	}
	if cache.db != nil {
		dbCtx, cancel := context.WithTimeout(ctx, time.Second)
		defer cancel()
		_, err := cache.db.Exec(dbCtx, `
INSERT INTO profile_cache (profile_id, user_id, created_at)
VALUES ($1, $2, $3)
ON CONFLICT (profile_id)
DO UPDATE SET
  created_at = EXCLUDED.created_at,
  user_id = CASE WHEN EXCLUDED.user_id <> '' THEN EXCLUDED.user_id ELSE profile_cache.user_id END`,
			profileID, userID, createdAt)
		if err != nil {
			cache.log.Printf("failed to save profile cache id=%s: %v", profileID, err)
		}
	}
	cache.warmRedis(ctx, []uuid.UUID{profileID})
}

func (cache *ProfileCache) DeleteProfile(ctx context.Context, event ProfileDeleteEvent) {
	if event.ProfileID == "" {
		cache.log.Printf("skipping profile delete event with empty profileId")
		return
	}
	profileID, err := uuid.Parse(event.ProfileID)
	if err != nil {
		cache.log.Printf("skipping profile delete event with invalid profileId=%q", event.ProfileID)
		return
	}
	if cache.db != nil {
		dbCtx, cancel := context.WithTimeout(ctx, time.Second)
		defer cancel()
		tag, err := cache.db.Exec(dbCtx, `DELETE FROM profile_cache WHERE profile_id = $1`, profileID)
		if err != nil {
			cache.log.Printf("failed to delete profile cache id=%s: %v", profileID, err)
		} else if tag.RowsAffected() == 0 {
			cache.log.Printf("profile cache not found for profileId=%s", profileID)
		}
	}
	if cache.redis != nil {
		redisCtx, cancel := context.WithTimeout(ctx, 250*time.Millisecond)
		defer cancel()
		if err := cache.redis.SRem(redisCtx, profileExistsSetKey, profileID.String()).Err(); err != nil {
			cache.log.Printf("failed to evict profile id %s from redis: %v", profileID, err)
		}
	}
}

func StartProfileConsumers(ctx context.Context, cfg Config, cache *ProfileCache, logger *log.Logger) {
	if !cfg.ProfileConsumersEnabled || cache == nil || cache.db == nil {
		logger.Printf("profile cache consumers disabled")
		return
	}
	startProfileCreateConsumer(ctx, cfg, cache, logger)
	startProfileDeleteConsumer(ctx, cfg, cache, logger)
}

func startProfileCreateConsumer(ctx context.Context, cfg Config, cache *ProfileCache, logger *log.Logger) {
	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:     cfg.KafkaBrokers,
		Topic:       cfg.ProfileCreatedTopic,
		GroupID:     "swipe-service",
		StartOffset: kafka.FirstOffset,
		MinBytes:    1,
		MaxBytes:    1 << 20,
	})
	go consumeProfileEvents(ctx, reader, logger, func(message kafka.Message) {
		var event ProfileCreateEvent
		if err := json.Unmarshal(message.Value, &event); err != nil {
			logger.Printf("failed to decode profile create event: %v", err)
			return
		}
		cache.SaveProfile(ctx, event)
	})
}

func startProfileDeleteConsumer(ctx context.Context, cfg Config, cache *ProfileCache, logger *log.Logger) {
	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:     cfg.KafkaBrokers,
		Topic:       cfg.ProfileDeletedTopic,
		GroupID:     "swipe-service",
		StartOffset: kafka.FirstOffset,
		MinBytes:    1,
		MaxBytes:    1 << 20,
	})
	go consumeProfileEvents(ctx, reader, logger, func(message kafka.Message) {
		var event ProfileDeleteEvent
		if err := json.Unmarshal(message.Value, &event); err != nil {
			logger.Printf("failed to decode profile delete event: %v", err)
			return
		}
		cache.DeleteProfile(ctx, event)
	})
}

func consumeProfileEvents(ctx context.Context, reader *kafka.Reader, logger *log.Logger, handle func(kafka.Message)) {
	defer reader.Close()
	for {
		message, err := reader.FetchMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			if errorsIsNoRows(err) {
				continue
			}
			logger.Printf("profile event consumer error: %v", err)
			time.Sleep(time.Second)
			continue
		}
		handle(message)
		if err := reader.CommitMessages(ctx, message); err != nil {
			logger.Printf("failed to commit profile event: %v", err)
		}
	}
}

func errorsIsNoRows(err error) bool {
	return err == pgx.ErrNoRows
}
