package com.tinder.profiles.infrastructure.persistence.profile.internal;

import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import com.tinder.profiles.infrastructure.cache.SharedProfileSnapshotCache;
import com.tinder.contracts.dto.Hobby;
import com.tinder.contracts.dto.SharedPhotoDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.profiles.application.profile.port.in.InternalProfileQuery;
import com.tinder.profiles.application.profile.model.PreferencesData;
import com.tinder.profiles.application.profile.query.InternalProfileView;
import com.tinder.profiles.infrastructure.persistence.photos.PhotoRepository;
import com.tinder.profiles.infrastructure.persistence.photos.SharedPhotoMapper;
import com.tinder.profiles.infrastructure.persistence.profile.mapper.SharedProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Slf4j
@Component
@RequiredArgsConstructor
public class JpaInternalProfileQueryAdapter implements InternalProfileQuery {

    private final ProfileRepository repo;
    private final PhotoRepository photoRepository;
    private final SharedProfileMapper sharedMapper;
    private final SharedPhotoMapper sharedPhotoMapper;
    private final SharedProfileRowMapper sharedProfileRowMapper;
    private final SharedProfileSnapshotCache sharedProfileSnapshotCache;

    @Override
    public List<InternalProfileView> search(UUID viewerId, SearchCriteria criteria, int limit) {
        log.debug("searchByViewerPrefs: viewer {} searching with prefs: minAge={}, maxAge={}, gender={}, limit={}",
                viewerId, criteria.minAge(), criteria.maxAge(), criteria.gender(), limit);

        if (!repo.existsById(viewerId)) {
            log.error("Viewer not found: {}", viewerId);
            throw new NoSuchElementException("Viewer not found: " + viewerId);
        }

        List<Object[]> matchingProfiles = repo.searchSharedProfileRowsByPreferences(
                viewerId,
                criteria.minAge(),
                criteria.maxAge(),
                criteria.gender(),
                limit
        );

        log.debug("searchByViewerPrefs: viewer {} found {} matching profiles", viewerId, matchingProfiles.size());

        List<SharedProfileDto> results = toSharedProfiles(matchingProfiles);
        sharedProfileSnapshotCache.putAll(results);
        return toViews(results);
    }

    @Override
    public List<InternalProfileView> getMany(List<UUID> ids) {
        log.debug("Fetching {} profiles by ID", ids.size());
        if (ids.isEmpty()) {
            return List.of();
        }

        List<SharedProfileDto> foundProfiles = sharedProfileSnapshotCache.getMany(
                ids,
                missingIds -> {
                    List<Object[]> rows = repo.findSharedProfileRowsByIds(missingIds);
                    List<UUID> foundIds = sharedProfileRowMapper.idsOf(rows);
                    return sharedProfileRowMapper.toDtosInOrder(
                            missingIds,
                            rows,
                            photosByProfileId(foundIds),
                            hobbiesByProfileId(foundIds)
                    );
                }
        );

        if (foundProfiles.size() < ids.size()) {
            Set<UUID> foundIds = foundProfiles.stream()
                    .map(SharedProfileDto::id)
                    .collect(Collectors.toSet());
            List<UUID> missingIds = ids.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            log.warn("Requested {} profiles, found only {}. Missing IDs: {}",
                    ids.size(), foundProfiles.size(), missingIds);
        }

        return toViews(foundProfiles);
    }

    @Override
    public List<InternalProfileView> getActiveUsers() {
        log.debug("Fetching all active users");

        List<SharedProfileDto> results = repo.findAllByIsDeletedFalse().stream()
                .map(sharedMapper::toSharedProfileDto)
                .toList();

        log.info("Found {} active users", results.size());
        return toViews(results);
    }

    /**
     * Maps a flat projection and stitches back the two collections it cannot
     * carry. Costs two extra batch queries per call — cheaper than hydrating the
     * profile entities, and without them the snapshot would claim every profile
     * has no photos and no hobbies.
     */
    private List<SharedProfileDto> toSharedProfiles(List<Object[]> rows) {
        List<UUID> profileIds = sharedProfileRowMapper.idsOf(rows);
        return sharedProfileRowMapper.toDtos(
                rows,
                photosByProfileId(profileIds),
                hobbiesByProfileId(profileIds));
    }

    private Map<UUID, List<SharedPhotoDto>> photosByProfileId(List<UUID> profileIds) {
        if (profileIds.isEmpty()) {
            return Map.of();
        }
        return sharedPhotoMapper.byProfileId(photoRepository.findAllByProfile_ProfileIdIn(profileIds));
    }

    private Map<UUID, List<Hobby>> hobbiesByProfileId(List<UUID> profileIds) {
        if (profileIds.isEmpty()) {
            return Map.of();
        }
        return sharedProfileRowMapper.hobbiesByProfileId(repo.findHobbyRowsByProfileIds(profileIds));
    }

    private List<InternalProfileView> toViews(List<SharedProfileDto> profiles) {
        return profiles.stream().map(this::toView).toList();
    }

    private InternalProfileView toView(SharedProfileDto profile) {
        InternalProfileView.LocationView location = profile.location() == null
                ? null
                : new InternalProfileView.LocationView(
                        profile.location().id(),
                        profile.location().latitude(),
                        profile.location().longitude(),
                        profile.location().city(),
                        profile.location().createdAt(),
                        profile.location().updatedAt());
        PreferencesData preferences = profile.preferences() == null
                ? null
                : new PreferencesData(
                        profile.preferences().minAge(),
                        profile.preferences().maxAge(),
                        profile.preferences().gender(),
                        profile.preferences().maxRange());
        List<InternalProfileView.PhotoView> photos = profile.photos() == null
                ? List.of()
                : profile.photos().stream()
                        .map(photo -> new InternalProfileView.PhotoView(
                                photo.photoId(),
                                photo.profileId(),
                                photo.s3Key(),
                                photo.isPrimary(),
                                photo.position(),
                                photo.url(),
                                photo.contentType(),
                                photo.size(),
                                photo.createdAt()))
                        .toList();
        List<String> hobbies = profile.hobbies() == null
                ? List.of()
                : profile.hobbies().stream().map(Enum::name).toList();
        return new InternalProfileView(
                profile.id(),
                profile.name(),
                profile.age(),
                profile.bio(),
                profile.city(),
                profile.isActive(),
                location,
                preferences,
                profile.isDeleted(),
                photos,
                hobbies);
    }
}
