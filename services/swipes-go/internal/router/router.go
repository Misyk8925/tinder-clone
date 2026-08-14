package router

import (
	"bytes"
	"context"
	"errors"
	"log"
	"net/http"
	"runtime/debug"

	"github.com/valyala/fasthttp"
	"tinder-clone/services/swipes-go/internal/metrics"
	"tinder-clone/services/swipes-go/internal/model"
	"tinder-clone/services/swipes-go/internal/security"
	"tinder-clone/services/swipes-go/internal/utils"
)

var (
	healthPath  = []byte("/actuator/health")
	metricsPath = []byte("/actuator/metrics")
	swipesPath  = []byte("/api/v1/swipes")
	superPath   = []byte("/api/v1/swipes/super")
)

type SwipeService interface {
	Send(context.Context, model.SwipeDTO, security.Principal, bool) error
}

type QueueReporter interface {
	QueueStats() (int, int)
}

type Router struct {
	service SwipeService
	auth    *security.Authenticator
	metrics *metrics.Metrics
	queue   QueueReporter
	log     *log.Logger
}

func New(service SwipeService, auth *security.Authenticator, serviceMetrics *metrics.Metrics, queue QueueReporter, logger *log.Logger) *Router {
	return &Router{service: service, auth: auth, metrics: serviceMetrics, queue: queue, log: logger}
}

func (router *Router) Handler(ctx *fasthttp.RequestCtx) {
	defer func() {
		if recovered := recover(); recovered != nil {
			router.log.Printf("HTTP handler panic: %v\n%s", recovered, debug.Stack())
			writeError(ctx, http.StatusInternalServerError, "Internal server error")
		}
	}()
	path := ctx.Path()
	switch {
	case bytes.Equal(path, healthPath):
		router.health(ctx)
	case bytes.Equal(path, metricsPath):
		router.renderMetrics(ctx)
	case bytes.Equal(path, swipesPath):
		router.swipe(ctx, false)
	case bytes.Equal(path, superPath):
		router.swipe(ctx, true)
	default:
		writeError(ctx, http.StatusNotFound, "Not found")
	}
}

func (router *Router) health(ctx *fasthttp.RequestCtx) {
	if !ctx.IsGet() {
		writeError(ctx, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}
	ctx.Response.Header.SetContentType("application/json")
	ctx.SetStatusCode(http.StatusOK)
	ctx.SetBodyString(`{"status":"UP"}`)
}

func (router *Router) renderMetrics(ctx *fasthttp.RequestCtx) {
	if !ctx.IsGet() {
		writeError(ctx, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}
	depth, capacity := router.queue.QueueStats()
	body := router.metrics.AppendPrometheus(ctx.Response.Body()[:0], depth, capacity)
	ctx.Response.Header.SetContentType("text/plain; version=0.0.4; charset=utf-8")
	ctx.SetStatusCode(http.StatusOK)
	ctx.SetBody(body)
}

func (router *Router) swipe(ctx *fasthttp.RequestCtx, superRoute bool) {
	if !ctx.IsPost() {
		writeError(ctx, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}
	principal, err := router.auth.Authenticate(context.Background(), ctx.Request.Header.Peek("Authorization"), ctx.Request.Header.Peek("X-Internal-Auth"))
	if err != nil {
		router.metrics.AuthFailed.Add(1)
		writeError(ctx, http.StatusUnauthorized, "Invalid JWT principal")
		return
	}
	dto, err := decodeSwipe(ctx.PostBody())
	if err != nil {
		router.metrics.Rejected.Add(1)
		writeError(ctx, http.StatusBadRequest, "Swipe body is required")
		return
	}
	if err := router.service.Send(context.Background(), dto, principal, superRoute); err != nil {
		writeHTTPError(ctx, err)
		return
	}
	ctx.SetStatusCode(http.StatusAccepted)
}

func decodeSwipe(body []byte) (model.SwipeDTO, error) {
	return utils.DecodeSwipe(body)
}

func writeHTTPError(ctx *fasthttp.RequestCtx, err error) {
	var httpErr model.HTTPError
	if errors.As(err, &httpErr) {
		writeError(ctx, httpErr.Status, httpErr.Reason)
		return
	}
	writeError(ctx, http.StatusInternalServerError, "Internal server error")
}

func writeError(ctx *fasthttp.RequestCtx, status int, reason string) {
	ctx.Response.Header.SetContentType("text/plain; charset=utf-8")
	ctx.SetStatusCode(status)
	ctx.SetBodyString(reason)
}
