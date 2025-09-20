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
    UUID id; // Добавляем поле id для корректной десериализации из Redis

    @NotNull
    Double latitude;

    @NotNull
    Double longitude;

    @JsonCreator // Указывает Jackson, как создавать экземпляр класса
    public LocationDto(
        @JsonProperty("id") UUID id, // Добавляем id в конструктор
        @JsonProperty("latitude") Double latitude,
        @JsonProperty("longitude") Double longitude
    ) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}