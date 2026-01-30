package com.tinder.profiles.profile.internal;

import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.profile.dto.profileData.SharedProfileDto;
import com.tinder.profiles.profile.mapper.SharedProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
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
        log.debug("Fetching {} profiles by IDs", ids.size());

        List<SharedProfileDto> results = repo.findAllById(ids).stream()
                .map(sharedMapper::toSharedProfileDto)
                .toList();

        if (results.size() < ids.size()) {
            log.warn("Requested {} profiles, found only {}. Some IDs may be invalid or deleted",
                    ids.size(), results.size());
        }

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
