package main

import (
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
)

const maxBodyBytes = 2 << 20

type APIServer struct {
	service *SwipeService
	auth    *Authenticator
	log     *log.Logger
}

func NewAPIServer(service *SwipeService, auth *Authenticator, logger *log.Logger) *APIServer {
	return &APIServer{service: service, auth: auth, log: logger}
}

func (server *APIServer) Routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/actuator/health", server.health)
	mux.HandleFunc("/api/v1/swipes", server.swipes(false))
	mux.HandleFunc("/api/v1/swipes/super", server.swipes(true))
	return mux
}

func (server *APIServer) health(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(`{"status":"UP"}`))
}

func (server *APIServer) swipes(superRoute bool) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
			return
		}

		internalRequest := server.auth.IsInternalRequest(r)
		if internalRequest && !superRoute {
			body, err := readTrustedBody(r)
			if err != nil {
				writeError(w, http.StatusBadRequest, "Swipe body is required")
				return
			}
			swipe, err := parseTrustedSwipe(body)
			if err != nil {
				writeHTTPError(w, err)
				return
			}
			if err := server.service.SendTrustedInternalSwipe(r.Context(), swipe, false); err != nil {
				writeHTTPError(w, err)
				return
			}
			w.WriteHeader(http.StatusAccepted)
			return
		}

		bearerToken := ""
		if !internalRequest {
			token, err := server.auth.BearerToken(r.Context(), r)
			if err != nil {
				writeHTTPError(w, err)
				return
			}
			bearerToken = token
		}

		dto, err := decodeSwipeDTO(w, r)
		if err != nil {
			writeHTTPError(w, err)
			return
		}

		if err := server.service.SendSwipe(r.Context(), dto, superRoute, bearerToken, internalRequest); err != nil {
			writeHTTPError(w, err)
			return
		}
		w.WriteHeader(http.StatusAccepted)
	}
}

func readTrustedBody(r *http.Request) ([]byte, error) {
	defer r.Body.Close()
	return io.ReadAll(r.Body)
}

func decodeSwipeDTO(w http.ResponseWriter, r *http.Request) (SwipeDTO, error) {
	reader := http.MaxBytesReader(w, r.Body, maxBodyBytes)
	defer reader.Close()
	decoder := json.NewDecoder(reader)
	var dto SwipeDTO
	if err := decoder.Decode(&dto); err != nil {
		return SwipeDTO{}, badRequest("Swipe body is required")
	}
	return dto, nil
}

func writeHTTPError(w http.ResponseWriter, err error) {
	var httpErr HTTPError
	if errors.As(err, &httpErr) {
		writeError(w, httpErr.Status, httpErr.Reason)
		return
	}
	writeError(w, http.StatusInternalServerError, "Internal server error")
}

func writeError(w http.ResponseWriter, status int, reason string) {
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.WriteHeader(status)
	_, _ = w.Write([]byte(reason))
}
