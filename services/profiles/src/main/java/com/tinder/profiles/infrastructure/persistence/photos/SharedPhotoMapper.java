package com.tinder.profiles.infrastructure.persistence.photos;

import com.tinder.contracts.dto.SharedPhotoDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The single home for {@link Photo} → {@link SharedPhotoDto} translation.
 *
 * <p>Both paths that build a {@code SharedProfileDto} need it: the entity path
 * ({@code CustomSharedProfileMapper}) and the flat-projection path
 * ({@code SharedProfileRowMapper}), which cannot carry a one-to-many in its row
 * and so fetches photos separately and groups them here.
 */
@Component
public class SharedPhotoMapper {

    /** Photos of a single profile, ordered by position. */
    public List<SharedPhotoDto> toDtos(UUID profileId, Collection<Photo> photos) {
        if (photos == null || photos.isEmpty()) {
            return List.of();
        }
        return photos.stream()
                .sorted(Comparator.comparingInt(Photo::getPosition))
                .map(photo -> toDto(profileId, photo))
                .toList();
    }

    /**
     * Groups photos by their owning profile, each list ordered by position.
     * Reads the owner id off the lazy {@code profile} reference, which serves the
     * identifier without initializing the proxy.
     */
    public Map<UUID, List<SharedPhotoDto>> byProfileId(Collection<Photo> photos) {
        if (photos == null || photos.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<Photo>> grouped = new LinkedHashMap<>();
        for (Photo photo : photos) {
            if (photo.getProfile() == null) {
                continue;
            }
            grouped.computeIfAbsent(photo.getProfile().getProfileId(), id -> new ArrayList<>())
                    .add(photo);
        }

        Map<UUID, List<SharedPhotoDto>> byProfileId = new LinkedHashMap<>(grouped.size());
        grouped.forEach((profileId, owned) -> byProfileId.put(profileId, toDtos(profileId, owned)));
        return byProfileId;
    }

    private SharedPhotoDto toDto(UUID profileId, Photo photo) {
        return new SharedPhotoDto(
                photo.getPhotoID(),
                profileId,
                photo.getS3Key(),
                photo.isPrimary(),
                photo.getPosition(),
                photo.getUrl(),
                photo.getContentType(),
                photo.getSize(),
                photo.getCreatedAt());
    }
}
