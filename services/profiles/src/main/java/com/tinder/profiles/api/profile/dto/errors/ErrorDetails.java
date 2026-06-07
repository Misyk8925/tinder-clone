package com.tinder.profiles.api.profile.dto.errors;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorDetails {
    private Violations[] violations;
}
