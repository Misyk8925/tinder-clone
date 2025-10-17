package com.tinder.profiles.profile;

import com.tinder.profiles.profile.dto.errors.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CustomErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {

       List<Violations> errors = ex.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(fieldError -> new Violations(
                        fieldError.getField(),
                        fieldError.getDefaultMessage(),
                        String.valueOf(fieldError.getRejectedValue())
                    ))
                    .toList();

        // Создаем детали ошибки
        ErrorDetails details = new ErrorDetails(errors.toArray(new Violations[0]));

        ErrorSummary summary = new ErrorSummary(
                "Request validation failed",
                "VALIDATION_ERROR"
        );

        CustomErrorResponse errorResponse = new CustomErrorResponse(summary, details);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }
}