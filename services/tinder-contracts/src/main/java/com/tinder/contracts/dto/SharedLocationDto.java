package com.tinder.contracts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Snapshot of a profile's location. Carried on Kafka events ({@code profile.created},
 * {@code profile.updated}) and on the internal search/deck scoring payload.
 * Coordinates are WGS-84 (EPSG:4326).
 *
 * @param id        surrogate PK from the location table; null if location was never persisted
 * @param latitude  WGS-84 latitude, range [-90, 90]
 * @param longitude WGS-84 longitude, range [-180, 180]
 * @param city      resolved city name from Nominatim; null if geocoding failed at write time
 * @param createdAt timestamp when the location record was first created (UTC)
 * @param updatedAt timestamp of the last location update (UTC)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SharedLocationDto(
        UUID id,

        @NotNull
        @DecimalMin("-90.0") @DecimalMax("90.0")
        Double latitude,

        @NotNull
        @DecimalMin("-180.0") @DecimalMax("180.0")
        Double longitude,

        String city,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
