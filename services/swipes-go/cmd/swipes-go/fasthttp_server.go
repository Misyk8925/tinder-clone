package main

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"time"

	"github.com/valyala/fasthttp"
)

type FastAPIServer struct {
	service *SwipeService
	auth    *Authenticator
	log     *log.Logger
}

func NewFastAPIServer(service *SwipeService, auth *Authenticator, logger *log.Logger) *FastAPIServer {
	return &FastAPIServer{service: service, auth: auth, log: logger}
}

func (server *FastAPIServer) Handler(ctx *fasthttp.RequestCtx) {
	path := string(ctx.Path())
	switch path {
	case "/actuator/health":
		server.health(ctx)
	case "/api/v1/swipes":
		server.swipes(ctx, false)
	case "/api/v1/swipes/super":
		server.swipes(ctx, true)
	default:
		writeFastError(ctx, fasthttp.StatusNotFound, "Not found")
	}
}

func (server *FastAPIServer) health(ctx *fasthttp.RequestCtx) {
	if !ctx.IsGet() {
		writeFastError(ctx, fasthttp.StatusMethodNotAllowed, "Method not allowed")
		return
	}
	ctx.Response.Header.SetContentType("application/json")
	ctx.SetStatusCode(fasthttp.StatusOK)
	ctx.SetBodyString(`{"status":"UP"}`)
}

func (server *FastAPIServer) swipes(ctx *fasthttp.RequestCtx, superRoute bool) {
	if !ctx.IsPost() {
		writeFastError(ctx, fasthttp.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	internalRequest := server.auth.IsInternalCandidate(string(ctx.Request.Header.Peek(internalAuthHeader)))
	requestContext := context.Background()
	if internalRequest && !superRoute {
		swipe, err := parseTrustedSwipe(ctx.PostBody())
		if err != nil {
			writeFastHTTPError(ctx, err)
			return
		}
		if err := server.service.SendTrustedInternalSwipe(requestContext, swipe, false); err != nil {
			writeFastHTTPError(ctx, err)
			return
		}
		ctx.SetStatusCode(fasthttp.StatusAccepted)
		return
	}

	bearerToken := ""
	if !internalRequest {
		token, err := server.auth.BearerTokenHeader(requestContext, string(ctx.Request.Header.Peek("Authorization")))
		if err != nil {
			writeFastHTTPError(ctx, err)
			return
		}
		bearerToken = token
	}

	var dto SwipeDTO
	if err := json.Unmarshal(ctx.PostBody(), &dto); err != nil {
		writeFastHTTPError(ctx, badRequest("Swipe body is required"))
		return
	}
	if err := server.service.SendSwipe(requestContext, dto, superRoute, bearerToken, internalRequest); err != nil {
		writeFastHTTPError(ctx, err)
		return
	}
	ctx.SetStatusCode(fasthttp.StatusAccepted)
}

func writeFastHTTPError(ctx *fasthttp.RequestCtx, err error) {
	var httpErr HTTPError
	if errors.As(err, &httpErr) {
		writeFastError(ctx, httpErr.Status, httpErr.Reason)
		return
	}
	writeFastError(ctx, fasthttp.StatusInternalServerError, "Internal server error")
}

func writeFastError(ctx *fasthttp.RequestCtx, status int, reason string) {
	ctx.Response.Header.SetContentType("text/plain; charset=utf-8")
	ctx.SetStatusCode(status)
	ctx.SetBodyString(reason)
}

func newFastHTTPServer(service *SwipeService, auth *Authenticator, logger *log.Logger) *fasthttp.Server {
	api := NewFastAPIServer(service, auth, logger)
	return &fasthttp.Server{
		Handler:            api.Handler,
		Name:               "swipes-go",
		ReadTimeout:        5 * time.Second,
		WriteTimeout:       5 * time.Second,
		MaxRequestBodySize: maxBodyBytes,
	}
}
