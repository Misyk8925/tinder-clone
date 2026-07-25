package com.tinder.profiles.infrastructure.persistence.profile.internal;

import com.tinder.profiles.infrastructure.persistence.preferences.PreferencesDto;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import com.tinder.profiles.infrastructure.cache.SharedProfileSnapshotCache;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.profiles.infrastructure.persistence.profile.mapper.SharedProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class InternalProfileService {

    private final ProfileRepository repo;
    private final SharedProfileMapper sharedMapper;
    private final SharedProfileRowMapper sharedProfileRowMapper;
    private final SharedProfileSnapshotCache sharedProfileSnapshotCache;

    public List<SharedProfileDto> searchByViewerPrefs(UUID viewerId, PreferencesDto prefs, int limit) {
        log.debug("searchByViewerPrefs: viewer {} searching with prefs: minAge={}, maxAge={}, gender={}, limit={}",
                viewerId, prefs.getMinAge(), prefs.getMaxAge(), prefs.getGender(), limit);

        if (!repo.existsById(viewerId)) {
            log.error("Viewer not found: {}", viewerId);
            throw new NoSuchElementException("Viewer not found: " + viewerId);
        }

        List<Object[]> matchingProfiles = repo.searchSharedProfileRowsByPreferences(
                viewerId,
                prefs.getMinAge(),
                prefs.getMaxAge(),
                prefs.getGender(),
                limit
        );

        log.debug("searchByViewerPrefs: viewer {} found {} matching profiles", viewerId, matchingProfiles.size());

        List<SharedProfileDto> results = sharedProfileRowMapper.toDtos(matchingProfiles);
        sharedProfileSnapshotCache.putAll(results);
        return results;
    }

    public List<SharedProfileDto> getMany(List<UUID> ids) {
        log.debug("Fetching {} profiles by ID", ids.size());
        if (ids.isEmpty()) {
            return List.of();
        }

        List<SharedProfileDto> foundProfiles = sharedProfileSnapshotCache.getMany(
                ids,
                missingIds -> sharedProfileRowMapper.toDtosInOrder(
                        missingIds,
                        repo.findSharedProfileRowsByIds(missingIds)
                )
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

        return foundProfiles;
    }

    public List<SharedProfileDto> getActiveUsers() {
        log.debug("Fetching all active users");

        List<SharedProfileDto> results = repo.findAllByIsDeletedFalse().stream()
                .map(sharedMapper::toSharedProfileDto)
                .toList();

        log.info("Found {} active users", results.size());
        return results;
    }
}
