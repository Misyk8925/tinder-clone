package com.tinder.profiles.infrastructure.persistence.photos;

import com.tinder.contracts.dto.SharedPhotoDto;
import com.tinder.profiles.infrastructure.photos.PhotoDownloadUrlSigner;
import com.tinder.profiles.infrastructure.photos.PhotoDownloadUrlSigner.PhotoLocator;
import lombok.RequiredArgsConstructor;
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
 *
 * <p>Every public method signs its photo URLs with one call to the photos
 * service, however many profiles and photos it maps.
 */
@Component
@RequiredArgsConstructor
public class SharedPhotoMapper {

    private final PhotoDownloadUrlSigner photoUrls;

    /** Photos of a single profile, ordered by position. */
    public List<SharedPhotoDto> toDtos(UUID profileId, Collection<Photo> photos) {
        if (photos == null || photos.isEmpty()) {
            return List.of();
        }
        return toDtosByProfileId(Map.of(profileId, photos)).get(profileId);
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
        return toDtosByProfileId(grouped);
    }

    /**
     * Maps photos already grouped by owning profile, each list ordered by position.
     * Profiles with no photos map to an empty list.
     */
    public Map<UUID, List<SharedPhotoDto>> toDtosByProfileId(Map<UUID, ? extends Collection<Photo>> photosByProfileId) {
        if (photosByProfileId == null || photosByProfileId.isEmpty()) {
            return Map.of();
        }

        List<PhotoLocator> locators = new ArrayList<>();
        photosByProfileId.forEach((profileId, owned) -> {
            if (owned != null) {
                owned.forEach(photo -> locators.add(locatorOf(profileId, photo)));
            }
        });
        Map<PhotoLocator, String> urls = photoUrls.forClient(locators);

        Map<UUID, List<SharedPhotoDto>> byProfileId = new LinkedHashMap<>(photosByProfileId.size());
        photosByProfileId.forEach((profileId, owned) -> byProfileId.put(profileId, owned == null
                ? List.of()
                : owned.stream()
                        .sorted(Comparator.comparingInt(Photo::getPosition))
                        .map(photo -> toDto(profileId, photo, urls.get(locatorOf(profileId, photo))))
                        .toList()));
        return byProfileId;
    }

    private static PhotoLocator locatorOf(UUID profileId, Photo photo) {
        return new PhotoLocator(profileId, photo.getS3Key(), photo.getUrl());
    }

    private static SharedPhotoDto toDto(UUID profileId, Photo photo, String url) {
        return new SharedPhotoDto(
                photo.getPhotoID(),
                profileId,
                photo.getS3Key(),
                photo.isPrimary(),
                photo.getPosition(),
                url,
                photo.getContentType(),
                photo.getSize(),
                photo.getCreatedAt());
    }
}
