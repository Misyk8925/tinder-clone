package com.tinder.deck.dto;



import java.util.UUID;

public record SharedProfileDto(UUID id,
                               String name,
                               Integer age,
                               String bio,
                               String city,
                               boolean isActive,
                               SharedLocationDto location,
                               SharedPreferencesDto preferences,
                               boolean isDeleted) {
}