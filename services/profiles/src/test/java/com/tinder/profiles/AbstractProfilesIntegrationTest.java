package com.tinder.profiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.util.TestKafkaConsumerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;

/**
 * Base class for integration tests that use Testcontainers for PostgreSQL
 * and connect to docker-compose Kafka/Redis.
 *
 * Provides:
 * - Static PostgreSQL Testcontainer
 * - @DynamicPropertySource wiring
 * - Common @Autowired fields
 * - @BeforeEach cleanup (Redis deck/jwt keys + DB rows)
 */
@Testcontainers
@Import(TestKafkaConsumerConfig.class)
public abstract class AbstractProfilesIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractProfilesIntegrationTest.class);

    static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";

    @Container
    static PostgreSQLContainer<?> postgresContainer;

    static {
        log.info("Using docker-compose Kafka: {}", KAFKA_BOOTSTRAP_SERVERS);
        postgresContainer = new PostgreSQLContainer<>(
                DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                        .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("profiles_test")
                .withUsername("test")
                .withPassword("test")
                .withCommand("postgres", "-c", "max_connections=200");
        postgresContainer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        registry.add("spring.kafka.bootstrap-servers", () -> KAFKA_BOOTSTRAP_SERVERS);
        registry.add("spring.kafka.producer.bootstrap-servers", () -> KAFKA_BOOTSTRAP_SERVERS);
        registry.add("spring.kafka.consumer.bootstrap-servers", () -> KAFKA_BOOTSTRAP_SERVERS);

        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ProfileRepository profileRepository;

    @Autowired
    protected com.tinder.profiles.preferences.PreferencesRepository preferencesRepository;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected TestKafkaConsumerConfig.TestKafkaEventCollector kafkaEventCollector;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Set<String> deckKeys = redisTemplate.keys("deck:*");
        if (deckKeys != null && !deckKeys.isEmpty()) {
            redisTemplate.delete(deckKeys);
        }
        Set<String> jwtCacheKeys = redisTemplate.keys("jwt:cache:*");
        if (jwtCacheKeys != null && !jwtCacheKeys.isEmpty()) {
            redisTemplate.delete(jwtCacheKeys);
        }

        profileRepository.deleteAll();
        preferencesRepository.deleteAll();

        kafkaEventCollector.reset();

        log.info("Test setup complete: Redis and database cleaned");
    }
}
