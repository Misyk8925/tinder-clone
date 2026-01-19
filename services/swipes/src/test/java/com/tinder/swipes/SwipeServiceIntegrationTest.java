package com.tinder.swipes;

import com.tinder.swipes.model.SwipeRecord;
import com.tinder.swipes.model.dto.SwipeRecordDto;
import com.tinder.swipes.model.embedded.SwipeRecordId;
import com.tinder.swipes.repository.SwipeRepository;
import com.tinder.swipes.service.SwipeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SwipeServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private SwipeService swipeService;

    @Autowired
    private SwipeRepository swipeRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private UUID user1;
    private UUID user2;
    private UUID user3;

    @BeforeEach
    void setUp() {
        user1 = UUID.randomUUID();
        user2 = UUID.randomUUID();
        user3 = UUID.randomUUID();

        // Clean up database and cache before each test
        swipeRepository.deleteAll();
        Objects.requireNonNull(redisTemplate.getConnectionFactory())
                .getConnection()
                .serverCommands()
                .flushAll();
    }

    @AfterEach
    void tearDown() {
        swipeRepository.deleteAll();
    }

    @Test
    void shouldSaveFirstSwipe() {
        // given
        SwipeRecordDto dto = new SwipeRecordDto(
                user1.toString(),
                user2.toString(),
                true
        );

        // when
        swipeService.save(dto);

        // then
        SwipeRecordId id = new SwipeRecordId(user1, user2);
        SwipeRecord saved = swipeRepository.findBySwipeRecordId(id);

        assertThat(saved).isNotNull();
        assertThat(saved.getDecision1()).isTrue();
        assertThat(saved.getDecision2()).isNull();
    }

    @Test
    void shouldSaveSecondSwipeAndCreateMatch() {
        // given - first swipe
        SwipeRecordDto firstSwipe = new SwipeRecordDto(
                user1.toString(),
                user2.toString(),
                true
        );
        swipeService.save(firstSwipe);

        // when - second swipe (reverse direction)
        SwipeRecordDto secondSwipe = new SwipeRecordDto(
                user2.toString(),
                user1.toString(),
                true
        );
        swipeService.save(secondSwipe);

        // then
        SwipeRecordId id = new SwipeRecordId(user1, user2);
        SwipeRecord saved = swipeRepository.findBySwipeRecordId(id);

        assertThat(saved).isNotNull();
        assertThat(saved.getDecision1()).isTrue();
        assertThat(saved.getDecision2()).isTrue();
    }

    @Test
    void shouldNotOverwriteExistingDecision2() {
        // given - both swipes already exist
        SwipeRecordId id = new SwipeRecordId(user1, user2);
        SwipeRecord existing = SwipeRecord.builder()
                .swipeRecordId(id)
                .decision1(true)
                .decision2(false)
                .build();
        swipeRepository.save(existing);

        // when - try to save again
        SwipeRecordDto dto = new SwipeRecordDto(
                user2.toString(),
                user1.toString(),
                true
        );
        swipeService.save(dto);

        // then - decision2 should remain unchanged
        SwipeRecord saved = swipeRepository.findBySwipeRecordId(id);
        assertThat(saved.getDecision2()).isFalse(); // not changed
    }

    @Test
    void shouldReturnEmptyMapForEmptyCandidateList() {
        // when
        Map<UUID, Boolean> result = swipeService.existsBetweenBatch(user1, Collections.emptyList());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldWarmUpCacheOnFirstRequest() {
        // given - some swipes exist in DB
        SwipeRecordId id1 = new SwipeRecordId(user1, user2);
        SwipeRecordId id2 = new SwipeRecordId(user1, user3);

        swipeRepository.save(SwipeRecord.builder()
                .swipeRecordId(id1)
                .decision1(true)
                .build());

        swipeRepository.save(SwipeRecord.builder()
                .swipeRecordId(id2)
                .decision1(false)
                .build());

        // when
        List<UUID> candidates = Arrays.asList(user2, user3);
        Map<UUID, Boolean> result = swipeService.existsBetweenBatch(user1, candidates);

        // then - should return correct results
        assertThat(result).hasSize(2);
        assertThat(result.get(user2)).isTrue();
        assertThat(result.get(user3)).isTrue();

        // and cache should be populated
        String cacheKey = "swipes:exists:" + user1;
        Set<Object> cached = redisTemplate.opsForSet().members(cacheKey);
        assertThat(cached)
                .isNotNull()
                .hasSize(2)
                .contains(user2.toString(), user3.toString());
    }

    @Test
    void shouldUseCacheOnSecondRequest() {
        // given - warm up cache first
        SwipeRecordId id = new SwipeRecordId(user1, user2);
        swipeRepository.save(SwipeRecord.builder()
                .swipeRecordId(id)
                .decision1(true)
                .build());

        List<UUID> candidates = List.of(user2);
        swipeService.existsBetweenBatch(user1, candidates);

        // when - delete from DB but cache still has data
        swipeRepository.deleteAll();
        Map<UUID, Boolean> result = swipeService.existsBetweenBatch(user1, candidates);

        // then - should still return true from cache
        assertThat(result.get(user2)).isTrue();
    }

    @Test
    void shouldHandleUserWithNoSwipes() {
        // given - user has no swipes
        List<UUID> candidates = Arrays.asList(user2, user3);

        // when
        Map<UUID, Boolean> result = swipeService.existsBetweenBatch(user1, candidates);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(user2)).isFalse();
        assertThat(result.get(user3)).isFalse();

        // cache should have EMPTY_MARKER
        String cacheKey = "swipes:exists" + ":" + user1;
        Set<Object> cached = redisTemplate.opsForSet().members(cacheKey);
        System.out.println("Cached members: " + cached);
        assertThat(cached).contains("EMPTY_MARKER");
    }

    @Test
    void shouldHandleMixedResults() {
        // given - user swiped on user2 but not user3
        SwipeRecordId id = new SwipeRecordId(user1, user2);
        swipeRepository.save(SwipeRecord.builder()
                .swipeRecordId(id)
                .decision1(true)
                .build());

        // when
        List<UUID> candidates = Arrays.asList(user2, user3);
        Map<UUID, Boolean> result = swipeService.existsBetweenBatch(user1, candidates);

        // then
        assertThat(result.get(user2)).isTrue();
        assertThat(result.get(user3)).isFalse();
    }

    @Test
    void shouldExpireCache() throws InterruptedException {
        // given
        SwipeRecordId id = new SwipeRecordId(user1, user2);
        swipeRepository.save(SwipeRecord.builder()
                .swipeRecordId(id)
                .decision1(true)
                .build());

        List<UUID> candidates = List.of(user2);
        swipeService.existsBetweenBatch(user1, candidates);

        // when - check TTL
        String cacheKey = "swipes:exists" + ":" + user1;
        Long ttl = redisTemplate.getExpire(cacheKey);

        // then - should have TTL set (24 hours = 86400 seconds)
        assertThat(ttl).isNotNull().isGreaterThan(0).isLessThanOrEqualTo(86400);
    }
}
