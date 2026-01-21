package com.tinder.deck.service.pipeline;


import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class CandidateSearchStage {

    private final ProfilesHttp profilesHttp;

    @Value("${deck.search-limit:2000}")
    private int searchLimit;

    @Value("${deck.request-timeout-ms:5000}")
    private long timeoutMs;

    @Value("${deck.retries:3}")
    private int retries;

    public Flux<SharedProfileDto> searchCandidates(SharedProfileDto viewer) {
        SharedPreferencesDto prefs = getPreferencesOrDefault(viewer);

        log.debug("Searching candidates for viewer {} with preferences: {}",
                viewer.id(), prefs);

        return profilesHttp.searchProfiles(viewer.id(), prefs, searchLimit)
                .timeout(Duration.ofMillis(timeoutMs))
                .retry(retries)
                .onErrorResume(e -> {
                    log.warn("Candidate search failed for viewer {}: {}",
                            viewer.id(), e.getMessage());
                    return Flux.empty();
                })
                .doOnComplete(() -> log.debug("Candidate search completed for viewer {}",
                        viewer.id()));
    }

    private SharedPreferencesDto getPreferencesOrDefault(SharedProfileDto viewer) {
        if (viewer.preferences() == null) {
            log.warn("Viewer {} has null preferences, using defaults", viewer.id());
            return new SharedPreferencesDto(18, 50, "ANY", 100);
        }
        return viewer.preferences();
    }

}
