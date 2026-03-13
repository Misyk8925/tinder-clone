package com.tinder.profiles.profile.exception;

import com.tinder.profiles.profile.dto.errors.ErrorSummary;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for profile-related exceptions.
 * Every error response automatically includes the Micrometer traceId from MDC so
 * the caller can reference it in ELK or Zipkin to reproduce the full request path.
 */
@RestControllerAdvice
@Slf4j
public class ProfileExceptionHandler {

    @ExceptionHandler(ProfileException.class)
    public ResponseEntity<ErrorSummary> handleProfileException(ProfileException ex) {
        log.error("Profile exception [traceId={}, userId={}, correlationId={}]: {} - {}",
                MDC.get("traceId"), MDC.get("userId"), MDC.get("correlationId"),
                ex.getErrorCode(), ex.getMessage());

        ErrorSummary errorSummary = ErrorSummary.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .build();

        return ResponseEntity
                .status(ex.getStatus())
                .body(errorSummary);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed [traceId={}, userId={}, correlationId={}]: {}",
                MDC.get("traceId"), MDC.get("userId"), MDC.get("correlationId"), errors);

        Map<String, Object> response = new HashMap<>();
        response.put("code", "VALIDATION_ERROR");
        response.put("message", "Validation failed");
        response.put("errors", errors);
        // Include traceId so the client can reference it in a support ticket
        response.put("traceId", MDC.get("traceId"));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorSummary> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException [traceId={}, userId={}]: {} - {}",
                MDC.get("traceId"), MDC.get("userId"), ex.getStatusCode(), ex.getReason());

        ErrorSummary errorSummary = ErrorSummary.builder()
                .code(ex.getStatusCode().toString())
                .message(ex.getReason() != null ? ex.getReason() : "An error occurred")
                .build();

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(errorSummary);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorSummary> handleGenericException(Exception ex) {
        log.error("Unexpected exception [traceId={}, userId={}, correlationId={}]",
                MDC.get("traceId"), MDC.get("userId"), MDC.get("correlationId"), ex);

        ErrorSummary errorSummary = ErrorSummary.builder()
                .code("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorSummary);
    }
}