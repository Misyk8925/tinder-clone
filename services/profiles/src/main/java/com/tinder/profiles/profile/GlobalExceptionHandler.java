package com.tinder.profiles.profile;

import com.tinder.profiles.profile.dto.errors.CustomErrorResponse;
import com.tinder.profiles.profile.dto.errors.ErrorDetails;
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

        List<String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getDefaultMessage())
            .toList();
        String errorString = String.join(", ", errors);

        ErrorDetails errorDetails = ErrorDetails.builder()
                .code("bad request")
                .message(errorString)
                .build();
        CustomErrorResponse errorResponse = new CustomErrorResponse(errorDetails);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }
}