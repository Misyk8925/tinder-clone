package com.tinder.profiles.profile.internal;

import com.tinder.contracts.dto.SharedLocationDto;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class SharedProfileRowMapper {

    public List<SharedProfileDto> toDtos(List<Object[]> rows) {
        return rows.stream()
                .map(this::toDto)
                .toList();
    }

    public List<SharedProfileDto> toDtosInOrder(List<UUID> requestedIds, List<Object[]> rows) {
        Map<UUID, SharedProfileDto> byId = new LinkedHashMap<>(rows.size());
        for (Object[] row : rows) {
            SharedProfileDto dto = toDto(row);
            byId.put(dto.id(), dto);
        }

        return requestedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private SharedProfileDto toDto(Object[] row) {
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
                uuid(row[0]),
                string(row[1]),
                integer(row[2]),
                string(row[3]),
                string(row[4]),
                bool(row[5]),
                location,
                preferences,
                bool(row[6]),
                Collections.emptyList(),
                Collections.emptyList()
        );
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
