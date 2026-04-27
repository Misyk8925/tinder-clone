package main

import "net/http"

type HTTPError struct {
	Status int
	Reason string
}

func (err HTTPError) Error() string {
	return err.Reason
}

func badRequest(reason string) HTTPError {
	return HTTPError{Status: http.StatusBadRequest, Reason: reason}
}

func forbidden(reason string) HTTPError {
	return HTTPError{Status: http.StatusForbidden, Reason: reason}
}

func unauthorized(reason string) HTTPError {
	return HTTPError{Status: http.StatusUnauthorized, Reason: reason}
}

func notFound(reason string) HTTPError {
	return HTTPError{Status: http.StatusNotFound, Reason: reason}
}

func tooManyRequests(reason string) HTTPError {
	return HTTPError{Status: http.StatusTooManyRequests, Reason: reason}
}
