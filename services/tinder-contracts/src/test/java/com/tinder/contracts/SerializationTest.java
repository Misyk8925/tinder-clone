package com.tinder.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tinder.contracts.dto.*;
import com.tinder.contracts.dto.Hobby;
import com.tinder.contracts.event.v1.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void sharedLocationDto_roundTrip() throws Exception {
        var dto = new SharedLocationDto(UUID.randomUUID(), 48.8566, 2.3522, "Paris",
                LocalDateTime.now(), LocalDateTime.now());
        assertEquals(dto, mapper.readValue(mapper.writeValueAsString(dto), SharedLocationDto.class));
    }

    @Test
    void sharedPreferencesDto_roundTrip() throws Exception {
        var dto = new SharedPreferencesDto(20, 35, "FEMALE", 50);
        assertEquals(dto, mapper.readValue(mapper.writeValueAsString(dto), SharedPreferencesDto.class));
    }

    @Test
    void sharedPhotoDto_roundTrip() throws Exception {
        var dto = new SharedPhotoDto(UUID.randomUUID(), UUID.randomUUID(), "photos/abc.jpg",
                true, 0, "https://cdn/abc.jpg", "image/jpeg", 102400L, LocalDateTime.now());
        assertEquals(dto, mapper.readValue(mapper.writeValueAsString(dto), SharedPhotoDto.class));
    }

    @Test
    void sharedProfileDto_roundTrip() throws Exception {
        var location = new SharedLocationDto(UUID.randomUUID(), 48.8566, 2.3522, "Paris", null, null);
        var prefs = new SharedPreferencesDto(20, 35, "FEMALE", 50);
        var dto = new SharedProfileDto(UUID.randomUUID(), "Alice", 28, "Hello", "Paris",
                true, location, prefs, false, List.of(), List.of(Hobby.HIKING, Hobby.GAMING));
        assertEquals(dto, mapper.readValue(mapper.writeValueAsString(dto), SharedProfileDto.class));
    }

    @Test
    void sharedSwipeRecordDto_roundTrip() throws Exception {
        var dto = new SharedSwipeRecordDto(UUID.randomUUID(), UUID.randomUUID(), true, null);
        assertEquals(dto, mapper.readValue(mapper.writeValueAsString(dto), SharedSwipeRecordDto.class));
    }

    @Test
    void profileCreatedEvent_roundTrip() throws Exception {
        var event = new ProfileCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), "user-1", Instant.now());
        assertEquals(event, mapper.readValue(mapper.writeValueAsString(event), ProfileCreatedEvent.class));
    }

    @Test
    void profileUpdatedEvent_roundTrip() throws Exception {
        var event = new ProfileUpdatedEvent(UUID.randomUUID(), UUID.randomUUID(),
                ChangeType.LOCATION_CHANGE, Set.of("city"), Instant.now(), null);
        assertEquals(event, mapper.readValue(mapper.writeValueAsString(event), ProfileUpdatedEvent.class));
    }

    @Test
    void profileDeletedEvent_roundTrip() throws Exception {
        var event = new ProfileDeletedEvent(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        assertEquals(event, mapper.readValue(mapper.writeValueAsString(event), ProfileDeletedEvent.class));
    }

    @Test
    void unknownFields_areIgnored() throws Exception {
        // simulates a producer adding a new field that this consumer version doesn't know about
        var json = """
                {"profileId":"%s","name":"Alice","age":28,"bio":null,"city":null,
                 "isActive":true,"location":null,"preferences":{"minAge":20,"maxAge":35,"gender":"FEMALE","maxRange":50},
                 "isDeleted":false,"photos":[],"hobbies":["HIKING"],"unknownFutureField":"value"}
                """.formatted(UUID.randomUUID());
        var dto = mapper.readValue(json, SharedProfileDto.class);
        assertEquals("Alice", dto.name());
    }
}
