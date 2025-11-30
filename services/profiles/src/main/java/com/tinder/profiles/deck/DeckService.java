package com.tinder.profiles.deck;

import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.profile.ProfileService;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.mapper.GetProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckService {

    private final DeckCacheReader cacheReader;
    private final ProfileRepository repo;
    private final GetProfileMapper getMapper;
    private final ProfileService profileService;

    public List<GetProfileDto> listWithProfiles(UUID viewerId, int offset, int limit) {

        List<UUID> deckUUIDs = cacheReader.readDeck(viewerId, offset, limit);

        if (!deckUUIDs.isEmpty()) {
            log.info("DECKSERVICE: Found {} profiles in cache for viewer {}", deckUUIDs.size(), viewerId);
            return getProfilesByIds(deckUUIDs);
        }

        // Fallback: build deck on the fly for new users
        log.info("DECKSERVICE: No cache found for viewer {}, building deck on the fly", viewerId);
        return buildDeckOnTheFly(viewerId, limit);
    }

    private List<GetProfileDto> getProfilesByIds(List<UUID> candidateIds) {

        List<Profile> profiles = repo.findAllById(candidateIds);

        Map<UUID, Profile> profileMap = profiles.stream()
                .collect(Collectors.toMap(Profile::getProfileId, p -> p));

        return candidateIds.stream()
                .map(profileMap::get)
                .filter(Objects::nonNull)
                .map(getMapper::toGetProfileDto)
                .collect(Collectors.toList());
    }

    private List<GetProfileDto> buildDeckOnTheFly(UUID userId, int limit) {

        Preferences preferences = repo.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("viewer not found"))
                .getPreferences();

        PreferencesDto prefs = new PreferencesDto(
                userId,
                preferences.getMinAge(),
                preferences.getMaxAge(),
                preferences.getGender(),
                preferences.getMaxRange()
        );

        List<GetProfileDto> candidates = profileService.searchByViewerPrefs(userId,prefs, limit);


        return candidates;
    }
}
