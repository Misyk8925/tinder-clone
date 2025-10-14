package com.tinder.deck.dto;

import java.util.UUID;

public record DeckProfileDto(
        UUID id,
        String name,
        Integer age,
        String bio,
        String city,
        SharedLocationDto location,
        Double score  // Релевантность из колоды
) {}