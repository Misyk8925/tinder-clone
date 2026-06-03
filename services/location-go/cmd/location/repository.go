package main

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Repository struct {
	pool *pgxpool.Pool
}

func NewRepository(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

const selectCols = `id, city, ST_Y(geo::geometry), ST_X(geo::geometry), created_at, updated_at`

func (r *Repository) FindByCity(ctx context.Context, city string) (*Location, error) {
	row := r.pool.QueryRow(ctx,
		`SELECT `+selectCols+` FROM location WHERE city = $1`, city)
	return scanLocation(row)
}

func (r *Repository) FindByID(ctx context.Context, id uuid.UUID) (*Location, error) {
	row := r.pool.QueryRow(ctx,
		`SELECT `+selectCols+` FROM location WHERE id = $1`, id)
	return scanLocation(row)
}

func (r *Repository) Save(ctx context.Context, loc *Location) (*Location, error) {
	if loc.ID == uuid.Nil {
		loc.ID = uuid.New()
	}
	now := time.Now().UTC()
	loc.CreatedAt = now
	loc.UpdatedAt = now

	_, err := r.pool.Exec(ctx,
		`INSERT INTO location(id, city, geo, created_at, updated_at)
		 VALUES($1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326), $5, $6)
		 ON CONFLICT DO NOTHING`,
		loc.ID, loc.City, loc.Lon, loc.Lat, loc.CreatedAt, loc.UpdatedAt,
	)
	if err != nil {
		return nil, err
	}

	// If ON CONFLICT triggered, the row already existed — fetch it
	saved, err := r.FindByID(ctx, loc.ID)
	if err != nil {
		return nil, err
	}
	if saved == nil {
		// Another concurrent insert won the race; look up by city
		return r.FindByCity(ctx, loc.City)
	}
	return saved, nil
}

func scanLocation(row pgx.Row) (*Location, error) {
	var l Location
	err := row.Scan(&l.ID, &l.City, &l.Lat, &l.Lon, &l.CreatedAt, &l.UpdatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &l, nil
}
