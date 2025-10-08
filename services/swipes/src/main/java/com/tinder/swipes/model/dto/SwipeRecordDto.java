package com.tinder.swipes.model.dto;

import com.tinder.swipes.model.SwipeRecord;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * DTO for {@link SwipeRecord}
 */
public record SwipeRecordDto(
        @NotNull String profile1Id,
        @NotNull String profile2Id,
        boolean decision) {
}