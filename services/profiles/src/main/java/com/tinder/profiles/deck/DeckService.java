package com.tinder.profiles.deck;

import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.profile.dto.profileData.shared.SharedProfileDto;
import com.tinder.profiles.profile.internal.InternalProfileService;
import com.tinder.profiles.profile.mapper.SharedProfileMapper;
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
    private final DeckClient deckClient;
    private final ProfileRepository repo;
    private final SharedProfileMapper sharedMapper;
    private final InternalProfileService internalProfileService;

    public List<SharedProfileDto> listWithProfiles(UUID viewerId, int offset, int limit) {
        DeckRequest request = new DeckRequest(viewerId, offset, limit);
        rememberViewerForScheduledDeckRefresh(request);

        return readProfilesFromCachedDeck(request, "cache")
                .or(() -> ensureDeckThenReadCache(request))
                .orElseGet(() -> buildEmergencyDeckOnTheFly(request));
    }

    private void rememberViewerForScheduledDeckRefresh(DeckRequest request) {
        cacheReader.markViewerActive(request.viewerId());
    }

    private Optional<List<SharedProfileDto>> readProfilesFromCachedDeck(DeckRequest request, String source) {
        List<UUID> candidateIds = cacheReader.readDeck(request.viewerId(), request.offset(), request.limit());
        if (candidateIds.isEmpty()) {
            return Optional.empty();
        }

        List<SharedProfileDto> profiles = getProfilesByIds(candidateIds);
        if (profiles.isEmpty()) {
            log.info("DECKSERVICE: {} returned only missing/deleted profiles for viewer {}",
                    source, request.viewerId());
            return Optional.empty();
        }

        log.info("DECKSERVICE: Loaded {} profiles from {} for viewer {}",
                profiles.size(), source, request.viewerId());
        return Optional.of(profiles);
    }

    private Optional<List<SharedProfileDto>> ensureDeckThenReadCache(DeckRequest request) {
        log.info("DECKSERVICE: No readable cached deck for viewer {}, requesting deck ensure", request.viewerId());

        if (!deckClient.ensureDeck(request.viewerId())) {
            return Optional.empty();
        }

        return readProfilesFromCachedDeck(request, "ensured deck");
    }

    private List<SharedProfileDto> buildEmergencyDeckOnTheFly(DeckRequest request) {
        log.info("DECKSERVICE: Falling back to on-the-fly deck for viewer {}", request.viewerId());
        return buildDeckOnTheFly(request.viewerId(), request.limit());
    }

    private List<SharedProfileDto> getProfilesByIds(List<UUID> candidateIds) {

        List<Profile> profiles = repo.findAllById(candidateIds);

        Map<UUID, Profile> profileMap = profiles.stream()
                .collect(Collectors.toMap(Profile::getProfileId, p -> p));

        return candidateIds.stream()
                .map(profileMap::get)
                .filter(Objects::nonNull)
                .filter(profile -> !profile.isDeleted())
                .map(sharedMapper::toSharedProfileDto)
                .collect(Collectors.toList());
    }

    private List<SharedProfileDto> buildDeckOnTheFly(UUID userId, int limit) {

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

        return internalProfileService.searchByViewerPrefs(userId, prefs, limit);
    }

    private record DeckRequest(UUID viewerId, int offset, int limit) {}
}
