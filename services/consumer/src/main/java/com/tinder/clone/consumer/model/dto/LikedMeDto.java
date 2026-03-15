package com.tinder.clone.consumer.model.dto;

import java.time.Instant;
import java.util.UUID;

public record LikedMeDto(UUID likerProfileId, Instant likedAt, boolean isSuper) {}
