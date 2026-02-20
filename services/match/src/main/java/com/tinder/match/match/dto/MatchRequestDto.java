package com.tinder.match.match.dto;

import java.util.UUID;

public record MatchRequestDto(UUID profile1Id, UUID profile2Id) {
}
