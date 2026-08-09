package com.tinder.profiles.infrastructure.persistence.profile.internal;

import com.tinder.contracts.dto.Hobby;
import com.tinder.contracts.dto.SharedLocationDto;
import com.tinder.contracts.dto.SharedPhotoDto;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds {@link SharedProfileDto} snapshots from the flat {@code Object[]}
 * projections on {@code ProfileRepository}.
 *
 * <p>The projection is one row per profile, so it cannot carry the photo
 * one-to-many or the {@code profile_hobbies} element collection. Those are
 * batch-loaded separately and handed in as lookups keyed by profile id — a
 * caller that passes empty maps produces snapshots with empty collections, which
 * consumers cannot distinguish from "this profile has no photos".
 */
@Slf4j
@Component
public class SharedProfileRowMapper {

    public List<SharedProfileDto> toDtos(
            List<Object[]> rows,
            Map<UUID, List<SharedPhotoDto>> photosByProfileId,
            Map<UUID, List<Hobby>> hobbiesByProfileId
    ) {
        return rows.stream()
                .map(row -> toDto(row, photosByProfileId, hobbiesByProfileId))
                .toList();
    }

    public List<SharedProfileDto> toDtosInOrder(
            List<UUID> requestedIds,
            List<Object[]> rows,
            Map<UUID, List<SharedPhotoDto>> photosByProfileId,
            Map<UUID, List<Hobby>> hobbiesByProfileId
    ) {
        Map<UUID, SharedProfileDto> byId = new LinkedHashMap<>(rows.size());
        for (Object[] row : rows) {
            SharedProfileDto dto = toDto(row, photosByProfileId, hobbiesByProfileId);
            byId.put(dto.id(), dto);
        }

        return requestedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Profile ids carried by a projection, for batch-loading the associations
     * that the projection itself cannot carry.
     */
    public List<UUID> idsOf(List<Object[]> rows) {
        return rows.stream().map(row -> uuid(row[0])).toList();
    }

    /**
     * Groups {@code (profile_id, hobby)} rows into a per-profile lookup. A stored
     * value that no longer matches a {@link Hobby} constant is logged and skipped
     * rather than failing the whole read.
     */
    public Map<UUID, List<Hobby>> hobbiesByProfileId(List<Object[]> hobbyRows) {
        if (hobbyRows == null || hobbyRows.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<Hobby>> byProfileId = new LinkedHashMap<>();
        for (Object[] row : hobbyRows) {
            Hobby hobby = hobby(string(row[1]));
            if (hobby == null) {
                continue;
            }
            byProfileId.computeIfAbsent(uuid(row[0]), id -> new ArrayList<>()).add(hobby);
        }
        return byProfileId;
    }

    private SharedProfileDto toDto(
            Object[] row,
            Map<UUID, List<SharedPhotoDto>> photosByProfileId,
            Map<UUID, List<Hobby>> hobbiesByProfileId
    ) {
        UUID profileId = uuid(row[0]);

        SharedLocationDto location = new SharedLocationDto(
                uuid(row[7]),
                doubleValue(row[8]),
                doubleValue(row[9]),
                string(row[10]),
                localDateTime(row[11]),
                localDateTime(row[12])
        );

        SharedPreferencesDto preferences = new SharedPreferencesDto(
                integer(row[13]),
                integer(row[14]),
                string(row[15]),
                integer(row[16])
        );

        return new SharedProfileDto(
                profileId,
                string(row[1]),
                integer(row[2]),
                string(row[3]),
                string(row[4]),
                bool(row[5]),
                location,
                preferences,
                bool(row[6]),
                photosByProfileId.getOrDefault(profileId, List.of()),
                hobbiesByProfileId.getOrDefault(profileId, List.of())
        );
    }

    private Hobby hobby(String storedValue) {
        if (storedValue == null) {
            return null;
        }
        try {
            return Hobby.valueOf(storedValue);
        } catch (IllegalArgumentException e) {
            log.warn("Skipping unknown stored hobby '{}'", storedValue);
            return null;
        }
    }

    private UUID uuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.valueOf(value.toString());
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private LocalDateTime localDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }
}
