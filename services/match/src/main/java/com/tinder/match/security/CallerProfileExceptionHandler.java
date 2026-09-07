package com.tinder.match.security;

import com.tinder.match.conversation.ConversationNotAccessibleException;
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
     * "No such conversation" and "you are not a participant" are one answer, so neither can be
     * used to probe conversation IDs.
     */
    @ExceptionHandler(ConversationNotAccessibleException.class)
    public ResponseEntity<Map<String, Object>> handleNotAccessible(ConversationNotAccessibleException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "CONVERSATION_NOT_FOUND",
                "message", ex.getMessage()
        ));
    }

    /**
     * Everything else the conversation layer raises is a bad request — a malformed message, a
     * missing id, an attachment that fails validation. Reporting those as 404 would tell the
     * caller their own valid conversation had vanished.
     */
    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(MessagingException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", "INVALID_CONVERSATION_REQUEST",
                "message", ex.getMessage()
        ));
    }
}
