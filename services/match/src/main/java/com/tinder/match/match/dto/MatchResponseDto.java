package com.tinder.match.match.dto;

import java.time.Instant;
import java.util.UUID;

public record MatchResponseDto(String id, UUID profile1Id, UUID profile2Id, Instant matchedAt) {
}
