package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

func main() {
	logger := log.New(os.Stdout, "swipes-go ", log.LstdFlags|log.LUTC)
	cfg, err := LoadConfig()
	if err != nil {
		logger.Fatalf("failed to load config: %v", err)
	}

	rootCtx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	producer, err := NewSwipeProducer(rootCtx, cfg, logger)
	if err != nil {
		logger.Fatalf("failed to create swipe producer: %v", err)
	}
	defer producer.Close()

	profileCache := NewProfileCache(rootCtx, cfg, logger)
	defer profileCache.Close()
	StartProfileConsumers(rootCtx, cfg, profileCache, logger)

	service := NewSwipeService(producer, profileCache, cfg.InternalBypassProfile)
	auth := NewAuthenticator(cfg, logger)
	if cfg.HTTPServerEngine == "fasthttp" {
		runFastHTTP(rootCtx, cfg, service, auth, logger)
		return
	}

	api := NewAPIServer(service, auth, logger)
	httpServer := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           api.Routes(),
		ReadHeaderTimeout: 2 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	go func() {
		logger.Printf("starting swipes-go on :%s", cfg.Port)
		if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Fatalf("http server failed: %v", err)
		}
	}()

	<-rootCtx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := httpServer.Shutdown(shutdownCtx); err != nil {
		logger.Printf("http shutdown failed: %v", err)
	}
}

func runFastHTTP(rootCtx context.Context, cfg Config, service *SwipeService, auth *Authenticator, logger *log.Logger) {
	server := newFastHTTPServer(service, auth, logger)
	go func() {
		logger.Printf("starting swipes-go fasthttp on :%s", cfg.Port)
		if err := server.ListenAndServe(":" + cfg.Port); err != nil {
			logger.Fatalf("fasthttp server failed: %v", err)
		}
	}()

	<-rootCtx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	done := make(chan struct{})
	go func() {
		if err := server.Shutdown(); err != nil {
			logger.Printf("fasthttp shutdown failed: %v", err)
		}
		close(done)
	}()
	select {
	case <-done:
	case <-shutdownCtx.Done():
		logger.Printf("fasthttp shutdown timed out")
	}
}
