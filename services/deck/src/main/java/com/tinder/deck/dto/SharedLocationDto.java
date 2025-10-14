package com.tinder.deck.dto;

import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.UUID;


public record SharedLocationDto(UUID id, Point geo, String city, LocalDateTime createdAt, LocalDateTime updatedAt) {
}