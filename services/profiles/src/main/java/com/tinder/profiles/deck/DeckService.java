package com.tinder.profiles.deck;

import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.profile.cache.DeckPageCacheService;
import com.tinder.profiles.profile.dto.profileData.deck.DeckProfileDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.profiles.profile.internal.InternalProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckService {

    private final DeckCacheReader cacheReader;
    private final DeckClient deckClient;
    private final ProfileRepository repo;
    private final InternalProfileService internalProfileService;
    private final DeckPageCacheService deckPageCacheService;

    public List<SharedProfileDto> listWithProfiles(UUID viewerId, int offset, int limit) {
        DeckRequest request = new DeckRequest(viewerId, offset, limit);
        rememberViewerForScheduledDeckRefresh(request);

        return readProfilesFromCachedDeck(request, "cache")
                .or(() -> ensureDeckThenReadCache(request))
                .orElseGet(() -> buildEmergencyDeckOnTheFly(request));
    }

    public List<DeckProfileDto> listDeckCards(UUID viewerId, int offset, int limit) {
        DeckRequest request = new DeckRequest(viewerId, offset, limit);
        rememberViewerForScheduledDeckRefresh(request);

        return readDeckCardsFromCachedDeck(request, "cache")
                .or(() -> ensureDeckThenReadDeckCardCache(request))
                .orElseGet(() -> buildEmergencyDeckCardsOnTheFly(request));
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

        log.debug("DECKSERVICE: Loaded {} profiles from {} for viewer {}",
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

    private Optional<List<DeckProfileDto>> readDeckCardsFromCachedDeck(DeckRequest request, String source) {
        List<UUID> candidateIds = cacheReader.readDeck(request.viewerId(), request.offset(), request.limit());
        if (candidateIds.isEmpty()) {
            return Optional.empty();
        }

        List<DeckProfileDto> profiles = internalProfileService.getDeckCards(candidateIds);
        if (profiles.isEmpty()) {
            log.info("DECKSERVICE: {} returned only missing/deleted deck profiles for viewer {}",
                    source, request.viewerId());
            return Optional.empty();
        }

        log.debug("DECKSERVICE: Loaded {} deck profiles from {} for viewer {}",
                profiles.size(), source, request.viewerId());
        return Optional.of(profiles);
    }

    public boolean prebuildDeckPage(UUID viewerId, int offset, int limit, boolean force) {
        if (!force && deckPageCacheService.get(viewerId, offset, limit) != null) {
            return true;
        }

        DeckRequest request = new DeckRequest(viewerId, offset, limit);
        Optional<List<DeckProfileDto>> deck = readDeckCardsFromCachedDeck(request, "prebuild");
        if (deck.isEmpty()) {
            return false;
        }

        deckPageCacheService.put(viewerId, offset, limit, deck.get());
        return true;
    }

    private Optional<List<DeckProfileDto>> ensureDeckThenReadDeckCardCache(DeckRequest request) {
        log.info("DECKSERVICE: No readable cached deck for viewer {}, requesting deck ensure", request.viewerId());

        if (!deckClient.ensureDeck(request.viewerId())) {
            return Optional.empty();
        }

        return readDeckCardsFromCachedDeck(request, "ensured deck");
    }

    private List<SharedProfileDto> buildEmergencyDeckOnTheFly(DeckRequest request) {
        log.info("DECKSERVICE: Falling back to on-the-fly deck for viewer {}", request.viewerId());
        return buildDeckOnTheFly(request.viewerId(), request.limit());
    }

    private List<DeckProfileDto> buildEmergencyDeckCardsOnTheFly(DeckRequest request) {
        log.info("DECKSERVICE: Falling back to on-the-fly deck cards for viewer {}", request.viewerId());
        return buildDeckCardsOnTheFly(request.viewerId(), request.limit());
    }

    private List<SharedProfileDto> getProfilesByIds(List<UUID> candidateIds) {
        return internalProfileService.getMany(candidateIds);
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

    private List<DeckProfileDto> buildDeckCardsOnTheFly(UUID userId, int limit) {

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

        return internalProfileService.searchDeckCardsByViewerPrefs(userId, prefs, limit);
    }

    private record DeckRequest(UUID viewerId, int offset, int limit) {}
}
