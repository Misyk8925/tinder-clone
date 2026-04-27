package main

import (
	"net/http"
	"testing"
)

func TestParseTrustedSwipe(t *testing.T) {
	body := []byte(`{"profile1Id":"a","profile2Id":"b","decision":true,"isSuper":false}`)
	swipe, err := parseTrustedSwipe(body)
	if err != nil {
		t.Fatalf("parseTrustedSwipe returned error: %v", err)
	}
	if swipe.Profile1ID != "a" || swipe.Profile2ID != "b" || !swipe.Decision || swipe.IsSuper {
		t.Fatalf("unexpected swipe: %+v", swipe)
	}
}

func TestParseTrustedSwipeDefaultsBooleans(t *testing.T) {
	body := []byte(`{"profile1Id":"a","profile2Id":"b"}`)
	swipe, err := parseTrustedSwipe(body)
	if err != nil {
		t.Fatalf("parseTrustedSwipe returned error: %v", err)
	}
	if swipe.Decision || swipe.IsSuper {
		t.Fatalf("expected false defaults: %+v", swipe)
	}
}

func TestParseTrustedSwipeRejectsMissingProfile(t *testing.T) {
	_, err := parseTrustedSwipe([]byte(`{"profile1Id":"a"}`))
	assertHTTPError(t, err, http.StatusBadRequest, "Missing field: profile2Id")
}

func TestParseTrustedSwipeRejectsNullSuper(t *testing.T) {
	_, err := parseTrustedSwipe([]byte(`{"profile1Id":"a","profile2Id":"b","isSuper":null}`))
	assertHTTPError(t, err, http.StatusBadRequest, "Invalid field: isSuper")
}

func assertHTTPError(t *testing.T, err error, status int, reason string) {
	t.Helper()
	if err == nil {
		t.Fatalf("expected error")
	}
	httpErr, ok := err.(HTTPError)
	if !ok {
		t.Fatalf("expected HTTPError, got %T", err)
	}
	if httpErr.Status != status || httpErr.Reason != reason {
		t.Fatalf("unexpected error: %+v", httpErr)
	}
}
