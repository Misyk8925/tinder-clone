package com.tinder.profiles.location;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

import java.util.UUID;

/**
 * DTO for {@link Location}
 */
@Value
public class LocationDto {
    UUID id;

    @NotNull
    Double latitude;

    @NotNull
    Double longitude;

    @JsonCreator
    public LocationDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("latitude") Double latitude,
        @JsonProperty("longitude") Double longitude
    ) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}