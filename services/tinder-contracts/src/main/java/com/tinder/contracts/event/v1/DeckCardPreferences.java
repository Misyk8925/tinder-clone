package com.tinder.contracts.event.v1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Discovery preferences embedded in a Deck Read card projection. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeckCardPreferences(
        @NotNull @Min(18) @Max(120) Integer minAge,
        @NotNull @Min(18) @Max(120) Integer maxAge,
        @NotNull String gender,
        @NotNull @Min(1) Integer maxDistanceKm
) {
}
