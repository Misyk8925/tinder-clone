package com.tinder.profiles.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ErrorDetails {
    private String message;
    private String code;
    private final String timestamp = LocalDateTime.now().toString();
}
