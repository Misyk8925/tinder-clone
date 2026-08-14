package main

import (
	"context"
	"errors"
	"log"
	"net"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/valyala/fasthttp"
	"tinder-clone/services/swipes-go/internal/config"
	swipekafka "tinder-clone/services/swipes-go/internal/kafka"
	"tinder-clone/services/swipes-go/internal/metrics"
	"tinder-clone/services/swipes-go/internal/repository"
	"tinder-clone/services/swipes-go/internal/router"
	"tinder-clone/services/swipes-go/internal/security"
	"tinder-clone/services/swipes-go/internal/service"
)

const maxBodyBytes = 2 << 20

func main() {
	logger := log.New(os.Stdout, "swipes-go ", log.LstdFlags|log.LUTC)
	cfg, err := config.Load()
	if err != nil {
		logger.Fatalf("configuration failed: %v", err)
	}
	rootCtx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	jwtValidator := security.NewJWTValidator(cfg.JWKSetURL, cfg.JWTIssuer, cfg.JWTAudience)
	startupCtx, cancelStartup := context.WithTimeout(rootCtx, 20*time.Second)
	defer cancelStartup()
	if err := jwtValidator.Initialize(startupCtx); err != nil {
		logger.Fatalf("JWT readiness failed: %v", err)
	}
	profileRepo, err := repository.NewProfiles(startupCtx, cfg.RedisAddr, cfg.DatabaseURL, cfg.ProfilesBaseURL, logger)
	if err != nil {
		logger.Fatalf("profile repository failed: %v", err)
	}
	defer profileRepo.Close()
	serviceMetrics := &metrics.Metrics{}
	producer, err := swipekafka.NewProducer(startupCtx, cfg, serviceMetrics, logger)
	if err != nil {
		logger.Fatalf("swipe producer failed: %v", err)
	}
	consumers := swipekafka.StartConsumers(rootCtx, cfg, profileRepo, serviceMetrics, logger)
	authenticator := security.NewAuthenticator(cfg.InternalAuthSecret, jwtValidator)
	swipeService := service.New(producer, profileRepo, serviceMetrics, cfg.InternalBypassProfile)
	httpRouter := router.New(swipeService, authenticator, serviceMetrics, producer, logger)
	server := &fasthttp.Server{
		Handler: httpRouter.Handler, Name: "swipes-go", ReadTimeout: 5 * time.Second,
		WriteTimeout: 5 * time.Second, IdleTimeout: 60 * time.Second, MaxRequestBodySize: maxBodyBytes,
	}
	listener, err := net.Listen("tcp", ":"+cfg.Port)
	if err != nil {
		logger.Fatalf("listen failed: %v", err)
	}
	serveErr := make(chan error, 1)
	go func() {
		logger.Printf("ready on :%s", cfg.Port)
		serveErr <- server.Serve(listener)
	}()
	select {
	case <-rootCtx.Done():
	case err := <-serveErr:
		if err != nil && !errors.Is(err, net.ErrClosed) {
			logger.Printf("HTTP server failed: %v", err)
		}
	}
	shutdownCtx, cancelShutdown := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancelShutdown()
	if err := server.ShutdownWithContext(shutdownCtx); err != nil {
		logger.Printf("HTTP shutdown failed: %v", err)
	}
	consumers.Close()
	if err := producer.Close(); err != nil {
		logger.Printf("Kafka producer shutdown failed: %v", err)
	}
}
