package com.tinder.contracts.event.v1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tinder.contracts.dto.Hobby;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Full client-facing card data owned by the Deck Read projection. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeckCardProjection(
        @NotNull UUID profileId,
        @NotBlank String name,
        @NotNull @Min(18) @Max(120) Integer age,
        String city,
        String bio,
        boolean isActive,
        @NotNull @Valid DeckCardPreferences preferences,
        @NotNull List<@Valid DeckCardPhoto> photos,
        @NotNull List<Hobby> hobbies
) {
    public DeckCardProjection {
        photos = photos == null ? List.of() : List.copyOf(photos);
        hobbies = hobbies == null ? List.of() : List.copyOf(hobbies);
    }
}
