package com.tinder.contracts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Cross-service profile snapshot used by the deck builder ({@code /internal/search},
 * {@code /by-ids}) and carried on Kafka events.
 *
 * <p>Note: {@code photos} contains only {@link SharedPhotoDto} — the JPA {@code Photo} and
 * {@code Hobby} entities from the profiles service are not exposed here by design.
 * Consumers that need hobbies should call {@code profiles /by-ids} directly.
 *
 * @param id          profile UUID; serialized as {@code "profileId"} for backward compatibility
 * @param name        display name
 * @param age         age in years
 * @param bio         free-text bio; may be null if the user has not set one
 * @param city        denormalized city name from the linked location; may be null
 * @param isActive    false if the profile has been deactivated
 * @param location    location snapshot; null if no location has been set
 * @param preferences discovery preferences
 * @param isDeleted   true if the profile has been soft-deleted
 * @param photos      ordered list of photos; empty if none uploaded
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SharedProfileDto(
        @JsonProperty("profileId") @NotNull UUID id,
        String name,
        Integer age,
        String bio,
        String city,
        boolean isActive,
        @Valid SharedLocationDto location,
        @Valid @NotNull SharedPreferencesDto preferences,
        boolean isDeleted,
        List<@Valid SharedPhotoDto> photos,
        List<Hobby> hobbies
) {}
