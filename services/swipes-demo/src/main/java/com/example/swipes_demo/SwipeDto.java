package com.example.swipes_demo;


import jakarta.validation.constraints.NotNull;

public record SwipeDto(
        @NotNull String profile1Id,
        @NotNull String profile2Id,
        boolean decision,
        Boolean isSuper) {  // nullable so clients that don't send isSuper default to false
}