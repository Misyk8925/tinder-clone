package com.tinder.deck.service.popularity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for LikeRateStore against a real Redis.
 *
 * <p>Two counters and a batched read are exactly the kind of thing that works in a mock and
 * fails in production, so these exercise the real hash operations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LikeRateStoreIntegrationTest {

    static GenericContainer<?> redisContainer;

    static {
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:8.2.1-alpine"))
                .withExposedPorts(6379);
        redisContainer.start();
    }

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @Autowired
    private LikeRateStore store;

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @BeforeEach
    void clean() {
        redis.delete(LikeRateStore.SHOWS_KEY, LikeRateStore.LIKES_KEY,
                LikeRateStore.TOTAL_SHOWS_KEY, LikeRateStore.TOTAL_LIKES_KEY).block();
    }

    @Test
    void reportsHowMuchEvidenceTheCountersHold() {
        // Given: two profiles, four impressions and two likes between them
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        store.observe(first, true).block();
        store.observe(first, false).block();
        store.observe(second, true).block();
        store.observe(second, false).block();

        LikeRateStore.Density density = store.density().block();

        assertThat(density).isNotNull();
        assertThat(density.profiles()).isEqualTo(2);
        assertThat(density.impressions()).isEqualTo(4);
        assertThat(density.likes()).isEqualTo(2);
        assertThat(density.likesPerProfile()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void doesNotCallForEmbeddingsBelowTheMeasuredCrossover() {
        // Given: one profile with a single like — nowhere near eight per profile
        store.observe(UUID.randomUUID(), true).block();

        LikeRateStore.Density density = store.density().block();

        // A factorisation fitted on this much evidence loses to the counter it would replace.
        assertThat(density).isNotNull();
        assertThat(density.readyForEmbeddings()).isFalse();
    }

    @Test
    void callsForEmbeddingsOnceThereIsEnoughEvidencePerProfile() {
        UUID profile = UUID.randomUUID();
        for (int i = 0; i < 20; i++) {
            store.observe(profile, i < 9).block();
        }

        LikeRateStore.Density density = store.density().block();

        assertThat(density).isNotNull();
        assertThat(density.likesPerProfile()).isGreaterThanOrEqualTo(LikeRateStore.EMBEDDING_THRESHOLD);
        assertThat(density.readyForEmbeddings()).isTrue();
    }

    @Test
    void restoresCountersFromHistoryAndForgetsWhatWasThere() {
        UUID profile = UUID.randomUUID();
        store.observe(profile, true).block();

        store.clear().block();
        store.restore(profile, 40, 10).block();
        store.restoreTotals(40, 10).block();

        LikeRates rates = store.ratesFor(List.of(profile)).block();
        LikeRateStore.Density density = store.density().block();

        assertThat(rates).isNotNull();
        assertThat(rates.rateOf(profile)).isCloseTo(11.0 / 48.0, within(1e-9));
        assertThat(density).isNotNull();
        assertThat(density.impressions()).isEqualTo(40);
    }

    @Test
    void reportsAnEmptyCounterWithoutFailing() {
        LikeRateStore.Density density = store.density().block();

        assertThat(density).isNotNull();
        assertThat(density.profiles()).isZero();
        assertThat(density.likesPerProfile()).isZero();
        assertThat(density.readyForEmbeddings()).isFalse();
    }

    @Test
    void countsAnImpressionForEverySwipeAndALikeOnlyForARightOne() {
        // Given: a profile shown four times and liked once
        UUID profile = UUID.randomUUID();
        store.observe(profile, true).block();
        store.observe(profile, false).block();
        store.observe(profile, false).block();
        store.observe(profile, false).block();

        // When / Then: (1 like + 1 prior) / (4 shows + 8 prior)
        LikeRates rates = store.ratesFor(List.of(profile)).block();
        assertThat(rates).isNotNull();
        assertThat(rates.rateOf(profile)).isCloseTo(2.0 / 12.0, within(1e-9));
    }

    @Test
    void ranksAWidelyLikedProfileAboveAWidelyIgnoredOne() {
        UUID liked = UUID.randomUUID();
        UUID ignored = UUID.randomUUID();
        for (int i = 0; i < 50; i++) {
            store.observe(liked, i % 2 == 0).block();
            store.observe(ignored, i % 25 == 0).block();
        }

        LikeRates rates = store.ratesFor(List.of(liked, ignored)).block();

        assertThat(rates).isNotNull();
        assertThat(rates.rateOf(liked)).isGreaterThan(rates.rateOf(ignored));
    }

    @Test
    void doesNotLetTwoLuckyImpressionsOutrankTwoHundredProvenOnes() {
        // Given: a profile liked on both of its two impressions
        UUID lucky = UUID.randomUUID();
        store.observe(lucky, true).block();
        store.observe(lucky, true).block();

        // and one liked half the time across two hundred
        UUID proven = UUID.randomUUID();
        for (int i = 0; i < 200; i++) {
            store.observe(proven, i % 2 == 0).block();
        }

        LikeRates rates = store.ratesFor(List.of(lucky, proven)).block();

        // Then: the prior is what stops a small sample from winning the deck
        assertThat(rates).isNotNull();
        assertThat(rates.rateOf(lucky)).isLessThan(rates.rateOf(proven));
    }

    @Test
    void givesAProfileNobodyHasSeenTheBaseRateRatherThanZero() {
        UUID unseen = UUID.randomUUID();

        LikeRates rates = store.ratesFor(List.of(unseen)).block();

        assertThat(rates).isNotNull();
        assertThat(rates.rateOf(unseen)).isEqualTo(store.baseRate()).isPositive();
    }

    @Test
    void readsAWholePoolInOneCall() {
        // Given: a pool of the size a deck build actually scores
        List<UUID> pool = java.util.stream.Stream.generate(UUID::randomUUID).limit(500).toList();
        store.observe(pool.get(0), true).block();

        LikeRates rates = store.ratesFor(pool).block();

        // Then: every candidate has a rate, seen or not
        assertThat(rates).isNotNull();
        assertThat(rates.rates()).hasSize(pool.size());
        assertThat(rates.rateOf(pool.get(0))).isGreaterThan(store.baseRate());
        assertThat(rates.rateOf(pool.get(1))).isEqualTo(store.baseRate());
    }

    @Test
    void marksOnlyProfilesWithNoImpressionsAsUnseen() {
        // Given: one profile shown once and never liked, and one never shown at all
        UUID shown = UUID.randomUUID();
        UUID never = UUID.randomUUID();
        store.observe(shown, false).block();

        LikeRates rates = store.ratesFor(List.of(shown, never)).block();

        // Then: a profile that was shown and passed over is not a newcomer, however low its
        // rate. Only zero impressions qualifies for a reserved slot.
        assertThat(rates).isNotNull();
        assertThat(rates.isUnseen(shown)).isFalse();
        assertThat(rates.isUnseen(never)).isTrue();
    }

    @Test
    void returnsNothingForAnEmptyPoolWithoutTouchingRedis() {
        LikeRates empty = store.ratesFor(List.of()).block();
        assertThat(empty).isNotNull();
        assertThat(empty.rates()).isEmpty();
    }
}
