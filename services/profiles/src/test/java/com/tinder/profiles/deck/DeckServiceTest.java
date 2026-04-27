package com.tinder.profiles.deck;

import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.profile.dto.profileData.shared.SharedLocationDto;
import com.tinder.profiles.profile.dto.profileData.shared.SharedPreferencesDto;
import com.tinder.profiles.profile.dto.profileData.shared.SharedProfileDto;
import com.tinder.profiles.profile.internal.InternalProfileService;
import com.tinder.profiles.profile.mapper.SharedProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeckServiceTest {

    @Mock
    private DeckCacheReader cacheReader;

    @Mock
    private DeckClient deckClient;

    @Mock
    private ProfileRepository repo;

    @Mock
    private SharedProfileMapper sharedMapper;

    @Mock
    private InternalProfileService internalProfileService;

    private DeckService deckService;

    private UUID viewerId;

    @BeforeEach
    void setUp() {
        deckService = new DeckService(cacheReader, deckClient, repo, sharedMapper, internalProfileService);
        viewerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should return cached deck without calling deck service")
    void shouldReturnCachedDeck() {
        UUID candidateId = UUID.randomUUID();
        Profile candidate = new Profile();
        candidate.setProfileId(candidateId);
        SharedProfileDto dto = createSharedProfile(candidateId);

        when(cacheReader.readDeck(viewerId, 0, 20)).thenReturn(List.of(candidateId));
        when(repo.findAllById(List.of(candidateId))).thenReturn(List.of(candidate));
        when(sharedMapper.toSharedProfileDto(candidate)).thenReturn(dto);

        List<SharedProfileDto> result = deckService.listWithProfiles(viewerId, 0, 20);

        assertThat(result).containsExactly(dto);
        verify(deckClient, never()).ensureDeck(any());
        verify(internalProfileService, never()).searchByViewerPrefs(any(), any(), anyInt());
    }

    @Test
    @DisplayName("Should ensure deck and reread cache on miss")
    void shouldEnsureDeckOnCacheMiss() {
        UUID candidateId = UUID.randomUUID();
        Profile candidate = new Profile();
        candidate.setProfileId(candidateId);
        SharedProfileDto dto = createSharedProfile(candidateId);

        when(cacheReader.readDeck(viewerId, 0, 20))
                .thenReturn(List.of())
                .thenReturn(List.of(candidateId));
        when(deckClient.ensureDeck(viewerId)).thenReturn(true);
        when(repo.findAllById(List.of(candidateId))).thenReturn(List.of(candidate));
        when(sharedMapper.toSharedProfileDto(candidate)).thenReturn(dto);

        List<SharedProfileDto> result = deckService.listWithProfiles(viewerId, 0, 20);

        assertThat(result).containsExactly(dto);
        verify(deckClient).ensureDeck(viewerId);
        verify(internalProfileService, never()).searchByViewerPrefs(any(), any(), anyInt());
    }

    @Test
    @DisplayName("Should ensure deck when cached IDs cannot be hydrated")
    void shouldEnsureDeckWhenCachedIdsCannotBeHydrated() {
        UUID staleCandidateId = UUID.randomUUID();
        UUID refreshedCandidateId = UUID.randomUUID();
        Profile refreshedCandidate = new Profile();
        refreshedCandidate.setProfileId(refreshedCandidateId);
        SharedProfileDto dto = createSharedProfile(refreshedCandidateId);

        when(cacheReader.readDeck(viewerId, 0, 20))
                .thenReturn(List.of(staleCandidateId))
                .thenReturn(List.of(refreshedCandidateId));
        when(repo.findAllById(List.of(staleCandidateId))).thenReturn(List.of());
        when(deckClient.ensureDeck(viewerId)).thenReturn(true);
        when(repo.findAllById(List.of(refreshedCandidateId))).thenReturn(List.of(refreshedCandidate));
        when(sharedMapper.toSharedProfileDto(refreshedCandidate)).thenReturn(dto);

        List<SharedProfileDto> result = deckService.listWithProfiles(viewerId, 0, 20);

        assertThat(result).containsExactly(dto);
        verify(deckClient).ensureDeck(viewerId);
        verify(internalProfileService, never()).searchByViewerPrefs(any(), any(), anyInt());
    }

    @Test
    @DisplayName("Should fall back to DB search when ensure does not yield a deck")
    void shouldFallbackWhenEnsureDoesNotProduceDeck() {
        Preferences preferences = Preferences.builder()
                .minAge(18)
                .maxAge(40)
                .gender("female")
                .maxRange(50)
                .build();
        Profile viewer = new Profile();
        viewer.setProfileId(viewerId);
        viewer.setPreferences(preferences);

        SharedProfileDto fallbackDto = createSharedProfile(UUID.randomUUID());

        when(cacheReader.readDeck(viewerId, 0, 20))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(deckClient.ensureDeck(viewerId)).thenReturn(false);
        when(repo.findById(viewerId)).thenReturn(Optional.of(viewer));
        when(internalProfileService.searchByViewerPrefs(eq(viewerId), any(), eq(20)))
                .thenReturn(List.of(fallbackDto));

        List<SharedProfileDto> result = deckService.listWithProfiles(viewerId, 0, 20);

        assertThat(result).containsExactly(fallbackDto);
        verify(deckClient).ensureDeck(viewerId);
        verify(internalProfileService).searchByViewerPrefs(eq(viewerId), any(), eq(20));
    }

    private SharedProfileDto createSharedProfile(UUID profileId) {
        return new SharedProfileDto(
                profileId,
                "name",
                25,
                "bio",
                "city",
                true,
                new SharedLocationDto(UUID.randomUUID(), 0.0, 0.0, "city", null, null),
                new SharedPreferencesDto(18, 40, "female", 50),
                false,
                List.of(),
                List.of()
        );
    }
}
