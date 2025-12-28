package com.tinder.profiles.profile.exception;

import com.tinder.profiles.profile.dto.errors.ErrorSummary;
import lombok.extern.slf4j.Slf4j;
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
 * Converts exceptions to proper HTTP responses with consistent error format.
 */
@RestControllerAdvice
@Slf4j
public class ProfileExceptionHandler {

    /**
     * Handles all ProfileException subclasses
     */
    @ExceptionHandler(ProfileException.class)
    public ResponseEntity<ErrorSummary> handleProfileException(ProfileException ex) {
        log.error("Profile exception: {} - {}", ex.getErrorCode(), ex.getMessage());

        ErrorSummary errorSummary = ErrorSummary.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .build();

        return ResponseEntity
                .status(ex.getStatus())
                .body(errorSummary);
    }

    /**
     * Handles Bean Validation errors (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("code", "VALIDATION_ERROR");
        response.put("message", "Validation failed");
        response.put("errors", errors);

        log.warn("Validation failed: {}", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    /**
     * Handles ResponseStatusException (for backward compatibility)
     * This should be phased out as code migrates to custom exceptions
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorSummary> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException (deprecated usage): {} - {}", ex.getStatusCode(), ex.getReason());

        ErrorSummary errorSummary = ErrorSummary.builder()
                .code(ex.getStatusCode().toString())
                .message(ex.getReason() != null ? ex.getReason() : "An error occurred")
                .build();

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(errorSummary);
    }

    /**
     * Handles unexpected exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorSummary> handleGenericException(Exception ex) {
        log.error("Unexpected exception: ", ex);

        ErrorSummary errorSummary = ErrorSummary.builder()
                .code("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorSummary);
    }
}

