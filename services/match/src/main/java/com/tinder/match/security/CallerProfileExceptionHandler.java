package com.tinder.match.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.MessagingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class CallerProfileExceptionHandler {

    @ExceptionHandler(CallerProfileResolver.UnknownCallerProfileException.class)
    public ResponseEntity<Map<String, Object>> handleUnknownCaller(
            CallerProfileResolver.UnknownCallerProfileException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", "PROFILE_NOT_RESOLVED",
                "message", ex.getMessage()
        ));
    }

    /**
     * Conversation lookups raise MessagingException for both "no such conversation" and
     * "you are not a participant" so the two stay indistinguishable to a caller probing IDs.
     */
    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<Map<String, Object>> handleMessaging(MessagingException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "CONVERSATION_UNAVAILABLE",
                "message", ex.getMessage()
        ));
    }
}
