package com.tinder.profiles.profile.dto.errors;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ErrorSummary {
    private String message;
    private String code;
    private final String timestamp = LocalDateTime.now().toString();
}
