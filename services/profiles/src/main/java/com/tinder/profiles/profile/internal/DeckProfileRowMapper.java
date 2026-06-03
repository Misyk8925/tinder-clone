package com.tinder.profiles.profile.internal;

import com.tinder.contracts.dto.Hobby;
import com.tinder.profiles.profile.dto.profileData.deck.DeckPhotoDto;
import com.tinder.profiles.profile.dto.profileData.deck.DeckProfileDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class DeckProfileRowMapper {

    public List<DeckProfileDto> toDtos(List<Object[]> rows) {
        return rowsToMap(rows).values().stream()
                .map(DeckProfileBuilder::build)
                .toList();
    }

    public List<DeckProfileDto> toDtosInOrder(List<UUID> requestedIds, List<Object[]> rows) {
        Map<UUID, DeckProfileBuilder> byId = rowsToMap(rows);
        return requestedIds.stream()
                .distinct()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(DeckProfileBuilder::build)
                .toList();
    }

    private Map<UUID, DeckProfileBuilder> rowsToMap(List<Object[]> rows) {
        Map<UUID, DeckProfileBuilder> byId = new LinkedHashMap<>();
        for (Object[] row : rows) {
            UUID profileId = uuid(row[0]);
            DeckProfileBuilder builder = byId.computeIfAbsent(profileId, ignored -> new DeckProfileBuilder(
                    profileId,
                    string(row[1]),
                    integer(row[2]),
                    string(row[3]),
                    string(row[4]),
                    string(row[5])
            ));

            if (row[6] != null && row[7] != null) {
                builder.addPhoto(new DeckPhotoDto(
                        uuid(row[6]),
                        string(row[7]),
                        integer(row[8]) == null ? 0 : integer(row[8]),
                        bool(row[9])
                ));
            }
            if (row[10] != null) {
                builder.addHobby(Hobby.valueOf(string(row[10])));
            }
        }
        return byId;
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

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static class DeckProfileBuilder {
        private final UUID id;
        private final String name;
        private final Integer age;
        private final String gender;
        private final String bio;
        private final String city;
        private final Map<UUID, DeckPhotoDto> photos = new LinkedHashMap<>();
        private final List<Hobby> hobbies = new ArrayList<>();

        private DeckProfileBuilder(UUID id, String name, Integer age, String gender, String bio, String city) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.gender = gender;
            this.bio = bio;
            this.city = city;
        }

        private void addPhoto(DeckPhotoDto photo) {
            photos.putIfAbsent(photo.photoId(), photo);
        }

        private void addHobby(Hobby hobby) {
            if (!hobbies.contains(hobby)) {
                hobbies.add(hobby);
            }
        }

        private DeckProfileDto build() {
            List<DeckPhotoDto> sortedPhotos = photos.values().stream()
                    .sorted(Comparator.comparingInt(DeckPhotoDto::position))
                    .toList();
            return new DeckProfileDto(id, name, age, gender, bio, city, sortedPhotos, List.copyOf(hobbies));
        }
    }
}
