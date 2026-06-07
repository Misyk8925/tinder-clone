package com.tinder.profiles.api.profile.dto.errors;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Violations {
    private String field;
    private String message;
    private String rejectedValue;
}
