package com.tinder.profiles.profile;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class IdsQueryParamParser {

    public static final int MAX_IDS = 100;

    public List<UUID> parse(String ids) {
        if (ids == null || ids.isBlank()) {
            throw new IllegalArgumentException("ids must not be blank");
        }

        List<UUID> parsedIds = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .toList();

        if (parsedIds.isEmpty()) {
            throw new IllegalArgumentException("ids must contain at least one UUID");
        }
        if (parsedIds.size() > MAX_IDS) {
            throw new IllegalArgumentException("ids exceeds maximum supported size");
        }

        return parsedIds;
    }
}
