package com.tinder.profiles.deck;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeckPagePrebuildScheduler {

    private final DeckCacheReader deckCacheReader;
    private final DeckService deckService;

    @Value("${profiles.cache.deck-page.prebuild.enabled:true}")
    private boolean enabled;

    @Value("${profiles.cache.deck-page.prebuild.recent-viewers-window-minutes:30}")
    private int recentViewersWindowMinutes;

    @Value("${profiles.cache.deck-page.prebuild.max-viewers:5000}")
    private int maxViewers;

    @Value("${profiles.cache.deck-page.prebuild.page-limit:20}")
    private int pageLimit;

    @Scheduled(
            fixedDelayString = "${profiles.cache.deck-page.prebuild.fixed-delay-ms:30000}",
            initialDelayString = "${profiles.cache.deck-page.prebuild.initial-delay-ms:15000}"
    )
    public void prebuildRecentViewerFirstPages() {
        if (!enabled) {
            return;
        }

        List<UUID> viewerIds = deckCacheReader.getRecentViewerIds(
                Duration.ofMinutes(recentViewersWindowMinutes),
                maxViewers
        );
        if (viewerIds.isEmpty()) {
            return;
        }

        int prebuilt = 0;
        int skipped = 0;
        for (UUID viewerId : viewerIds) {
            if (deckService.prebuildDeckPage(viewerId, 0, pageLimit, false)) {
                prebuilt++;
            } else {
                skipped++;
            }
        }

        log.debug("Prebuilt first deck pages for recent viewers: prebuilt={}, skipped={}, candidates={}",
                prebuilt, skipped, viewerIds.size());
    }
}
