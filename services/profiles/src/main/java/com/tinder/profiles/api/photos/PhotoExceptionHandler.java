package com.tinder.profiles.api.photos;

import com.tinder.profiles.api.profile.dto.errors.ErrorSummary;
import com.tinder.profiles.application.photos.exception.PhotoAccessDeniedException;
import com.tinder.profiles.application.photos.exception.PhotoException;
import com.tinder.profiles.application.photos.exception.PhotoNotFoundException;
import com.tinder.profiles.application.photos.exception.PhotoStorageException;
import com.tinder.profiles.application.photos.exception.PhotoValidationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;

/**
 * Maps photo failures onto HTTP status codes, mirroring the profile feature's
 * handler so both features answer with the same {@link ErrorSummary} shape.
 */
@RestControllerAdvice(assignableTypes = ProfilePhotoController.class)
@Slf4j
public class PhotoExceptionHandler {

    @ExceptionHandler(PhotoException.class)
    public ResponseEntity<ErrorSummary> handlePhotoException(PhotoException ex) {
        log.warn("Photo operation failed [traceId={}, userId={}]: {} - {}",
                MDC.get("traceId"), MDC.get("userId"), ex.getErrorCode(), ex.getMessage());

        return ResponseEntity
                .status(statusFor(ex))
                .body(ErrorSummary.builder()
                        .code(ex.getErrorCode())
                        .message(ex.getMessage())
                        .build());
    }

    /** A path variable could not be converted, e.g. a photo id that is not a UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorSummary> handleUnparseablePathVariable(
            MethodArgumentTypeMismatchException ex) {
        log.warn("Rejected photo request with unparseable '{}': {}", ex.getName(), ex.getValue());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorSummary.builder()
                        .code("INVALID_PHOTO_ID")
                        .message("Invalid %s: %s".formatted(ex.getName(), ex.getValue()))
                        .build());
    }

    /** Reading the multipart body failed — the upload never reached the use case. */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorSummary> handleUnreadableUpload(IOException ex) {
        log.error("Failed to read uploaded photo [traceId={}]", MDC.get("traceId"), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorSummary.builder()
                        .code("UPLOAD_FAILED")
                        .message("Failed to process image upload")
                        .build());
    }

    private HttpStatus statusFor(PhotoException ex) {
        if (ex instanceof PhotoNotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (ex instanceof PhotoAccessDeniedException) {
            return HttpStatus.FORBIDDEN;
        }
        if (ex instanceof PhotoValidationException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof PhotoStorageException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
