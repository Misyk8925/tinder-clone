package com.tinder.contracts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A profile's discovery preferences. Carried on the internal search payload and used
 * by the deck builder to filter candidates.
 *
 * @param minAge   minimum age of candidates to show, inclusive
 * @param maxAge   maximum age of candidates to show, inclusive
 * @param gender   gender of candidates to show (e.g. {@code "MALE"}, {@code "FEMALE"}, {@code "ALL"})
 * @param maxRange maximum search radius in kilometres
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SharedPreferencesDto(
        @NotNull @Min(18) @Max(100) Integer minAge,
        @NotNull @Min(18) @Max(100) Integer maxAge,
        @NotNull String gender,
        @NotNull @Min(1) Integer maxRange
) {}
