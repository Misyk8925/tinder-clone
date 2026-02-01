package com.tinder.profiles.profile.internal;

import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.profile.dto.profileData.shared.SharedProfileDto;
import com.tinder.profiles.profile.mapper.SharedProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class InternalProfileService {

    private final ProfileRepository repo;
    private final SharedProfileMapper sharedMapper;


    public List<SharedProfileDto> fetchPage(int page, int size) {
        log.debug("Fetching page {} with size {}", page, size);
        Pageable pageable = PageRequest.of(page, size);

        List<SharedProfileDto> results = repo.findAll(pageable).stream()
                .map(sharedMapper::toSharedProfileDto)
                .toList();

        log.debug("Fetched {} profiles for page {}", results.size(), page);
        return results;
    }
    public List<SharedProfileDto> searchByViewerPrefs(UUID viewerId, PreferencesDto prefs, int limit) {
        log.debug("searchByViewerPrefs: viewer {} searching with prefs: minAge={}, maxAge={}, gender={}, limit={}",
                viewerId, prefs.getMinAge(), prefs.getMaxAge(), prefs.getGender(), limit);

        // Verify viewer exists
        repo.findById(viewerId)
                .orElseThrow(() -> {
                    log.error("Viewer not found: {}", viewerId);
                    return new NoSuchElementException("Viewer not found: " + viewerId);
                });

        // Use database-level filtering for efficiency
        Pageable pageable = PageRequest.of(0, limit);
        List<Profile> matchingProfiles = repo.searchByPreferences(
                viewerId,
                prefs.getMinAge(),
                prefs.getMaxAge(),
                prefs.getGender(),
                pageable
        );

        log.debug("searchByViewerPrefs: viewer {} found {} matching profiles", viewerId, matchingProfiles.size());

        return matchingProfiles.stream()
                .map(sharedMapper::toSharedProfileDto)
                .toList();
    }

    public List<SharedProfileDto> getMany(List<UUID> ids) {
        log.info(">>> InternalProfileService.getMany called with {} IDs", ids.size());
        for (int i = 0; i < Math.min(5, ids.size()); i++) {
            log.info(">>>   Requested ID[{}]: {}", i, ids.get(i));
        }

        log.info(">>> Calling repo.findAllById...");
        List<Profile> foundProfiles = repo.findAllById(ids);
        log.info(">>> repo.findAllById returned {} profiles", foundProfiles.size());

        for (int i = 0; i < Math.min(5, foundProfiles.size()); i++) {
            Profile p = foundProfiles.get(i);
            log.info(">>>   Found profile[{}]: id={}, name={}, deleted={}",
                    i, p.getProfileId(), p.getName(), p.isDeleted());
        }

        // Log missing profiles if any
        if (foundProfiles.size() < ids.size()) {
            java.util.Set<UUID> foundIds = foundProfiles.stream()
                    .map(Profile::getProfileId)
                    .collect(java.util.stream.Collectors.toSet());

            List<UUID> missingIds = ids.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();

            log.warn(">>> MISSING PROFILES DETECTED!");
            log.warn(">>> Requested {} profiles, found only {}. Missing {} IDs:",
                    ids.size(), foundProfiles.size(), missingIds.size());
            for (int i = 0; i < Math.min(10, missingIds.size()); i++) {
                log.warn(">>>   Missing ID[{}]: {}", i, missingIds.get(i));
            }

            // Check if these IDs exist at all in DB
            log.info(">>> Checking if missing IDs exist in DB at all...");
            long totalProfilesInDb = repo.count();
            log.info(">>> Total profiles in database: {}", totalProfilesInDb);

            // Log actual UUIDs in database
            if (totalProfilesInDb > 0 && totalProfilesInDb <= 20) {
                log.info(">>> Fetching ALL profile IDs from database for comparison...");
                List<Profile> allProfiles = repo.findAll();
                log.info(">>> First 10 actual profile IDs in DB:");
                for (int i = 0; i < Math.min(10, allProfiles.size()); i++) {
                    log.info(">>>   DB Profile[{}]: id={}, name={}",
                            i, allProfiles.get(i).getProfileId(), allProfiles.get(i).getName());
                }

                // Compare with requested IDs
                Set<UUID> dbIds = allProfiles.stream()
                        .map(Profile::getProfileId)
                        .collect(java.util.stream.Collectors.toSet());

                log.info(">>> Comparison:");
                for (int i = 0; i < Math.min(5, missingIds.size()); i++) {
                    UUID requestedId = missingIds.get(i);
                    boolean existsInDb = dbIds.contains(requestedId);
                    log.info(">>>   Requested ID[{}]: {} - exists in DB: {}",
                            i, requestedId, existsInDb);
                }
            }

        } else {
            log.info(">>> SUCCESS: Found all {} requested profiles", foundProfiles.size());
        }

        List<SharedProfileDto> results = foundProfiles.stream()
                .map(sharedMapper::toSharedProfileDto)
                .toList();

        log.info(">>> Returning {} SharedProfileDto objects", results.size());
        return results;
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
