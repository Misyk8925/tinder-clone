package com.tinder.clone.consumer;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * How often each profile was shown and liked, for rebuilding the deck's like-rate counters.
 *
 * <p>Distinct from {@code /internal/experiment-outcomes}, which counts what each viewer *did*.
 * This counts what was done *to* each profile — the same table read down the other column.
 *
 * <p>Internal mTLS surface, restricted to the deck client by {@code ConsumerSecurityConfig}.
 */
@RestController
public class ProfileSwipeCountsController {

    /** A page bound, because a full rebuild walks every profile that has ever been swiped on. */
    private static final int MAX_LIMIT = 50_000;

    private final ProfileSwipeCountsRepository repository;

    public ProfileSwipeCountsController(ProfileSwipeCountsRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/internal/profile-swipe-counts")
    public List<ProfileSwipeCountDto> counts(
            @RequestParam(value = "after", required = false) String after,
            @RequestParam(value = "limit", defaultValue = "10000") int limit) {
        return repository.countsAfter(after, Math.min(Math.max(limit, 1), MAX_LIMIT));
    }

    /**
     * @param profileId   the profile that was swiped on
     * @param impressions how many times it was shown to someone who then swiped
     * @param likes       how many of those were right swipes
     */
    public record ProfileSwipeCountDto(String profileId, long impressions, long likes) {
    }
}
