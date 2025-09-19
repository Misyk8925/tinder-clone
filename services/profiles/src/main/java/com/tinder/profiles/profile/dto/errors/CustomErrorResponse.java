package com.tinder.profiles.profile.dto.errors;

import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public class CustomErrorResponse {
    private ErrorDetails error;
}
