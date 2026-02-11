package com.tinder.clone.consumer.model.dto;

import jakarta.validation.constraints.NotNull;


public record SwipeRecordDto(
        @NotNull String profile1Id,
        @NotNull String profile2Id,
        boolean decision) {
}