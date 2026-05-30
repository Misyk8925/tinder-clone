package com.tinder.contracts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Identifies a swipe interaction between two profiles.
 *
 * @param profile1Id  UUID of the first profile in the pair (lower UUID by convention)
 * @param profile2Id  UUID of the second profile in the pair
 * @param decision1   swipe decision made by profile1 on profile2; null if not yet decided
 * @param decision2   swipe decision made by profile2 on profile1; null if not yet decided
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SharedSwipeRecordDto(
        @NotNull UUID profile1Id,
        @NotNull UUID profile2Id,
        Boolean decision1,
        Boolean decision2
) {}
