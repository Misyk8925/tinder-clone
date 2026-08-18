package repository

import (
	"context"
	"fmt"
	"log"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
	"tinder-clone/services/swipes-go/internal/client"
	"tinder-clone/services/swipes-go/internal/model"
)

const profileExistsSetKey = "profiles:exists"

type Profiles struct {
	redis  *redis.Client
	db     *pgxpool.Pool
	remote *client.Profiles
	log    *log.Logger
}

func NewProfiles(ctx context.Context, redisAddr, databaseURL, profilesBaseURL string, logger *log.Logger) (*Profiles, error) {
	if redisAddr == "" || databaseURL == "" {
		return nil, fmt.Errorf("Redis and PostgreSQL configuration are required")
	}
	redisClient := redis.NewClient(&redis.Options{Addr: redisAddr})
	redisCtx, cancelRedis := context.WithTimeout(ctx, 2*time.Second)
	defer cancelRedis()
	if err := redisClient.Ping(redisCtx).Err(); err != nil {
		_ = redisClient.Close()
		return nil, fmt.Errorf("Redis readiness: %w", err)
	}

	dbCtx, cancelDB := context.WithTimeout(ctx, 5*time.Second)
	defer cancelDB()
	pool, err := pgxpool.New(dbCtx, databaseURL)
	if err != nil {
		_ = redisClient.Close()
		return nil, fmt.Errorf("PostgreSQL configuration: %w", err)
	}
	if err := pool.Ping(dbCtx); err != nil {
		pool.Close()
		_ = redisClient.Close()
		return nil, fmt.Errorf("PostgreSQL readiness: %w", err)
	}
	var tableName *string
	if err := pool.QueryRow(dbCtx, `SELECT to_regclass('public.profile_cache')::text`).Scan(&tableName); err != nil || tableName == nil {
		pool.Close()
		_ = redisClient.Close()
		return nil, fmt.Errorf("central migration V1_swipes.sql is not applied")
	}
	return &Profiles{redis: redisClient, db: pool, remote: client.NewProfiles(profilesBaseURL), log: logger}, nil
}

func (repo *Profiles) Close() {
	_ = repo.redis.Close()
	repo.db.Close()
}

func (repo *Profiles) ExistsAll(ctx context.Context, first, second uuid.UUID, bearer string) (bool, error) {
	if first == second {
		return false, nil
	}
	ids := []uuid.UUID{first, second}
	found := make(map[uuid.UUID]bool, 2)
	redisCtx, cancelRedis := context.WithTimeout(ctx, 250*time.Millisecond)
	redisFound, err := repo.redis.SMIsMember(redisCtx, profileExistsSetKey, first.String(), second.String()).Result()
	cancelRedis()
	if err == nil {
		for i, exists := range redisFound {
			if exists {
				found[ids[i]] = true
			}
		}
		if len(found) == 2 {
			return true, nil
		}
	}

	missing := missingIDs(found, ids)
	dbFound := repo.findInDB(ctx, missing)
	for _, id := range dbFound {
		found[id] = true
	}
	_ = repo.warmRedis(ctx, dbFound)
	missing = missingIDs(found, ids)
	if len(missing) == 0 {
		return true, nil
	}
	remoteFound, err := repo.remote.Existing(ctx, missing, bearer)
	if err != nil {
		return false, err
	}
	for _, id := range remoteFound {
		found[id] = true
	}
	repo.populateUnknown(ctx, remoteFound)
	return len(found) == 2, nil
}

func (repo *Profiles) ProfileIDForUser(ctx context.Context, userID, bearer string) (uuid.UUID, error) {
	dbCtx, cancel := context.WithTimeout(ctx, 750*time.Millisecond)
	defer cancel()
	var id uuid.UUID
	err := repo.db.QueryRow(dbCtx, `SELECT profile_id FROM profile_cache WHERE user_id = $1`, userID).Scan(&id)
	if err == nil {
		return id, nil
	}
	if err != pgx.ErrNoRows {
		repo.log.Printf("profile ownership lookup failed: %v", err)
	}
	id, remoteUserID, err := repo.remote.Mine(ctx, bearer)
	if err != nil {
		return uuid.Nil, err
	}
	if remoteUserID != "" && remoteUserID != userID {
		return uuid.Nil, fmt.Errorf("profiles /me subject mismatch")
	}
	if err := repo.save(ctx, id, userID, time.Now()); err != nil {
		return uuid.Nil, err
	}
	_ = repo.warmRedis(ctx, []uuid.UUID{id})
	return id, nil
}

func (repo *Profiles) SaveProfile(ctx context.Context, event model.ProfileCreateEvent) error {
	id, err := uuid.Parse(event.ProfileID)
	if err != nil {
		return err
	}
	userID := "unknown"
	if event.UserID != nil && *event.UserID != "" {
		userID = *event.UserID
	}
	createdAt := time.Now()
	if event.Timestamp != nil {
		createdAt = *event.Timestamp
	}
	if err := repo.save(ctx, id, userID, createdAt); err != nil {
		return err
	}
	return repo.warmRedis(ctx, []uuid.UUID{id})
}

func (repo *Profiles) DeleteProfile(ctx context.Context, event model.ProfileDeleteEvent) error {
	id, err := uuid.Parse(event.ProfileID)
	if err != nil {
		return err
	}
	dbCtx, cancelDB := context.WithTimeout(ctx, time.Second)
	_, dbErr := repo.db.Exec(dbCtx, `DELETE FROM profile_cache WHERE profile_id = $1`, id)
	cancelDB()
	redisCtx, cancelRedis := context.WithTimeout(ctx, 250*time.Millisecond)
	redisErr := repo.redis.SRem(redisCtx, profileExistsSetKey, id.String()).Err()
	cancelRedis()
	if dbErr != nil || redisErr != nil {
		return fmt.Errorf("profile eviction failed profile=%s db=%v redis=%v", id, dbErr, redisErr)
	}
	return nil
}

func (repo *Profiles) findInDB(ctx context.Context, ids []uuid.UUID) []uuid.UUID {
	if len(ids) == 0 {
		return nil
	}
	second := ids[0]
	if len(ids) > 1 {
		second = ids[1]
	}
	dbCtx, cancel := context.WithTimeout(ctx, 750*time.Millisecond)
	defer cancel()
	rows, err := repo.db.Query(dbCtx, `SELECT profile_id FROM profile_cache WHERE profile_id = $1 OR profile_id = $2`, ids[0], second)
	if err != nil {
		return nil
	}
	defer rows.Close()
	found := make([]uuid.UUID, 0, len(ids))
	for rows.Next() {
		var id uuid.UUID
		if rows.Scan(&id) == nil {
			found = append(found, id)
		}
	}
	return found
}

func (repo *Profiles) save(ctx context.Context, id uuid.UUID, userID string, createdAt time.Time) error {
	dbCtx, cancel := context.WithTimeout(ctx, time.Second)
	defer cancel()
	_, err := repo.db.Exec(dbCtx, `INSERT INTO profile_cache(profile_id, user_id, created_at) VALUES ($1,$2,$3)
		ON CONFLICT(profile_id) DO UPDATE SET created_at=EXCLUDED.created_at,
		user_id=CASE WHEN EXCLUDED.user_id <> 'unknown' THEN EXCLUDED.user_id ELSE profile_cache.user_id END`, id, userID, createdAt)
	if err != nil {
		repo.log.Printf("profile cache write failed: %v", err)
	}
	return err
}

func (repo *Profiles) populateUnknown(ctx context.Context, ids []uuid.UUID) {
	for _, id := range ids {
		_ = repo.save(ctx, id, "unknown", time.Now())
	}
	_ = repo.warmRedis(ctx, ids)
}

func (repo *Profiles) warmRedis(ctx context.Context, ids []uuid.UUID) error {
	if len(ids) == 0 {
		return nil
	}
	members := make([]any, len(ids))
	for i, id := range ids {
		members[i] = id.String()
	}
	redisCtx, cancel := context.WithTimeout(ctx, 250*time.Millisecond)
	defer cancel()
	if err := repo.redis.SAdd(redisCtx, profileExistsSetKey, members...).Err(); err != nil {
		repo.log.Printf("Redis warmup failed: %v", err)
		return err
	}
	return nil
}

func missingIDs(found map[uuid.UUID]bool, ids []uuid.UUID) []uuid.UUID {
	missing := make([]uuid.UUID, 0, len(ids))
	for _, id := range ids {
		if !found[id] {
			missing = append(missing, id)
		}
	}
	return missing
}
