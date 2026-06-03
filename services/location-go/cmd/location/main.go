package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/jackc/pgx/v5/pgxpool"
)

func main() {
	logger := log.New(os.Stdout, "location ", log.LstdFlags|log.LUTC)

	cfg := LoadConfig()

	if cfg.DatabaseURL == "" {
		logger.Fatal("DATABASE_URL / SPRING_DATASOURCE_URL is required")
	}

	rootCtx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	pool, err := pgxpool.New(rootCtx, cfg.DatabaseURL)
	if err != nil {
		logger.Fatalf("failed to connect to database: %v", err)
	}
	defer pool.Close()

	if err := pool.Ping(rootCtx); err != nil {
		logger.Fatalf("database ping failed: %v", err)
	}
	logger.Println("connected to database")

	repo := NewRepository(pool)
	geocoder := NewGeocoder(cfg, logger)
	svc := NewLocationService(repo, geocoder, logger)
	handler := NewHandler(svc, logger)

	runServer(rootCtx, ":"+cfg.Port, handler.Routes(), logger)
}
