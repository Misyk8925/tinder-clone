package com.tinder.match.moderation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ContentBlockedExceptionHandler {
    @ExceptionHandler(ContentBlockedException.class)
    public ResponseEntity<Map<String, Object>> handle(ContentBlockedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "code", "CONTENT_BLOCKED",
                "message", ex.getMessage()
        ));
    }
}
