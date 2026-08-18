package com.tinder.profiles;

import com.tinder.profiles.infrastructure.persistence.location.Location;
import com.tinder.profiles.infrastructure.persistence.location.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.stream.Stream;

/** Shared PostgreSQL, Kafka and Redis Testcontainers for every Spring context test in Profiles. */
public abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("profiles_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres", "-c", "max_connections=200");

    static final KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    static final GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:8.2.1-alpine"))
            .withExposedPorts(6379);

    static final String KAFKA_BOOTSTRAP_SERVERS;

    static {
        Startables.deepStart(Stream.of(postgresContainer, kafkaContainer, redisContainer)).join();
        KAFKA_BOOTSTRAP_SERVERS = kafkaContainer.getBootstrapServers();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", () -> KAFKA_BOOTSTRAP_SERVERS);
        registry.add("spring.kafka.producer.bootstrap-servers", () -> KAFKA_BOOTSTRAP_SERVERS);
        registry.add("spring.kafka.consumer.bootstrap-servers", () -> KAFKA_BOOTSTRAP_SERVERS);
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
        // Extra Tomcat connector stays off so parallel Spring contexts do not bind 8011.
        // gRPC mTLS uses keystores generated in generate-test-resources.
        registry.add("internal.server.ssl.enabled", () -> "false");
    }

    @Autowired(required = false)
    private LocationRepository locationRepository;

    @BeforeEach
    void seedLocalLocationFallbacks() {
        if (locationRepository == null) {
            return;
        }
        seedLocation("Vienna", 16.3738, 48.2082);
        seedLocation("Amstetten", 14.8721, 48.1226);
        seedLocation("Linz", 14.2858, 48.3069);
    }

    private void seedLocation(String city, double longitude, double latitude) {
        locationRepository.findByCity(city).orElseGet(() -> {
            GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
            var point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
            point.setSRID(4326);
            return locationRepository.save(Location.builder().city(city).geo(point).build());
        });
    }
}
