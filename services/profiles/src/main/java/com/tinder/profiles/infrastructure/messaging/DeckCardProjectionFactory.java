package com.tinder.profiles.infrastructure.messaging;

import com.tinder.contracts.event.v1.DeckCardPhoto;
import com.tinder.contracts.event.v1.DeckCardPreferences;
import com.tinder.contracts.event.v1.DeckCardProjection;
import com.tinder.contracts.event.v1.ProfileDeckCardProjectionEvent;
import com.tinder.contracts.event.v1.ProfileProjectionOperation;
import com.tinder.contracts.event.v1.ProjectionSource;
import com.tinder.profiles.infrastructure.persistence.photos.PhotoRepository;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

/** Builds the single canonical full-card event shape for LIVE and BACKFILL paths. */
@Component
@RequiredArgsConstructor
public class DeckCardProjectionFactory {

    private final ProfileRepository profileRepository;
    private final PhotoRepository photoRepository;

    public ProfileDeckCardProjectionEvent build(
            UUID profileId,
            ProfileProjectionOperation operation,
            ProjectionSource source,
            UUID backfillRunId
    ) {
        ProfileJpaEntity profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalStateException("Profile not found for projection: " + profileId));

        var photos = photoRepository.findAllByProfile_ProfileIdOrderByPositionAsc(profileId);
        return build(profile, photos, operation, source, backfillRunId);
    }

    public List<ProfileDeckCardProjectionEvent> buildBatch(
            List<UUID> orderedProfileIds,
            ProfileProjectionOperation operation,
            ProjectionSource source,
            UUID backfillRunId
    ) {
        Map<UUID, ProfileJpaEntity> profiles = profileRepository.findAllById(orderedProfileIds).stream()
                .collect(Collectors.toMap(ProfileJpaEntity::getProfileId, Function.identity()));
        Map<UUID, List<com.tinder.profiles.infrastructure.persistence.photos.Photo>> photos =
                photoRepository.findAllByProfile_ProfileIdIn(orderedProfileIds).stream()
                        .collect(Collectors.groupingBy(photo -> photo.getProfile().getProfileId()));

        return orderedProfileIds.stream()
                .map(profileId -> {
                    ProfileJpaEntity profile = profiles.get(profileId);
                    if (profile == null) {
                        throw new IllegalStateException("Profile disappeared during projection page: " + profileId);
                    }
                    ProfileProjectionOperation effectiveOperation = source == ProjectionSource.BACKFILL
                            && profile.isDeleted()
                            ? ProfileProjectionOperation.DELETE
                            : operation;
                    return build(profile, photos.getOrDefault(profileId, List.of()),
                            effectiveOperation, source, backfillRunId);
                })
                .toList();
    }

    private ProfileDeckCardProjectionEvent build(
            ProfileJpaEntity profile,
            Collection<com.tinder.profiles.infrastructure.persistence.photos.Photo> profilePhotos,
            ProfileProjectionOperation operation,
            ProjectionSource source,
            UUID backfillRunId
    ) {
        UUID profileId = profile.getProfileId();

        var preferences = profile.getPreferences();
        if (preferences == null) {
            throw new IllegalStateException("Profile has no preferences for projection: " + profileId);
        }

        var photos = profilePhotos.stream()
                .sorted(Comparator.comparingInt(photo -> photo.getPosition()))
                .map(photo -> new DeckCardPhoto(photo.getPhotoID(), photo.getUrl(), photo.getPosition()))
                .toList();

        DeckCardProjection card = new DeckCardProjection(
                profileId,
                profile.getName(),
                profile.getAge(),
                profile.getCity(),
                profile.getBio(),
                profile.isActive() && !profile.isDeleted(),
                new DeckCardPreferences(
                        preferences.getMinAge(),
                        preferences.getMaxAge(),
                        preferences.getGender(),
                        preferences.getMaxRange()),
                photos,
                profile.getHobbies());

        long version = profile.getVersion() == null ? 1L : Math.max(1L, profile.getVersion());
        return new ProfileDeckCardProjectionEvent(
                UUID.randomUUID(),
                profileId,
                profile.getUserId(),
                version,
                Instant.now(),
                operation,
                source,
                backfillRunId,
                card);
    }
}
