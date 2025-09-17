package com.tinder.profiles.profile;

import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public class CustomErrorResponse {
    private ErrorDetails error;
}
