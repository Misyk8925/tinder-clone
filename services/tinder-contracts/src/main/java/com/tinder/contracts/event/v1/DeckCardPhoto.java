package com.tinder.contracts.event.v1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** One display-ready photo embedded in a Deck Read card projection. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeckCardPhoto(
        @NotNull UUID photoId,
        @NotBlank String url,
        @Min(0) int order
) {
}
