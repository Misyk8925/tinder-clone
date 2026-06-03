package main

import (
	"encoding/json"
	"log"
	"net/http"
	"strings"

	"github.com/google/uuid"
)

type Handler struct {
	svc *LocationService
	log *log.Logger
}

func NewHandler(svc *LocationService, logger *log.Logger) *Handler {
	return &Handler{svc: svc, log: logger}
}

func (h *Handler) Routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", h.health)
	mux.HandleFunc("/actuator/health", h.health)
	mux.HandleFunc("/api/v1/locations/resolve", h.resolve)
	mux.HandleFunc("/api/v1/locations/", h.getByID)
	return mux
}

func (h *Handler) health(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "UP"})
}

func (h *Handler) resolve(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var req ResolveRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}
	defer r.Body.Close()

	hasCoords := req.Latitude != nil && req.Longitude != nil

	var loc *Location
	var err error
	if hasCoords {
		city := req.City
		if city == "" {
			city = "Unknown"
		}
		loc, err = h.svc.ResolveFromCoords(r.Context(), *req.Latitude, *req.Longitude, city)
	} else {
		if req.City == "" {
			writeError(w, http.StatusBadRequest, "city or coordinates are required")
			return
		}
		loc, err = h.svc.Resolve(r.Context(), req.City)
	}

	if err != nil {
		h.log.Printf("resolve error: %v", err)
		writeError(w, http.StatusInternalServerError, "failed to resolve location")
		return
	}

	writeJSON(w, http.StatusOK, toResponse(loc))
}

func (h *Handler) getByID(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	// path: /api/v1/locations/{id}
	rawID := strings.TrimPrefix(r.URL.Path, "/api/v1/locations/")
	id, err := uuid.Parse(rawID)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid location id")
		return
	}

	loc, err := h.svc.GetByID(r.Context(), id)
	if err != nil {
		h.log.Printf("getByID error: %v", err)
		writeError(w, http.StatusInternalServerError, "failed to get location")
		return
	}
	if loc == nil {
		writeError(w, http.StatusNotFound, "location not found")
		return
	}

	writeJSON(w, http.StatusOK, toResponse(loc))
}

func writeJSON(w http.ResponseWriter, status int, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.WriteHeader(status)
	_, _ = w.Write([]byte(msg))
}
