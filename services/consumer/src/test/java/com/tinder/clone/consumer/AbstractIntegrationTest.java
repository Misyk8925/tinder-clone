package com.tinder.clone.consumer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;

import java.util.stream.Stream;

/**
 * Shared PostgreSQL and Redis for every Spring context test in Consumer.
 * Containers are started once in a static block (not {@code @Container}) so
 * Testcontainers does not stop them between test classes while Spring still
 * caches the datasource URL.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("consumer_test")
                    .withUsername("test")
                    .withPassword("test");

    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    static {
        Startables.deepStart(Stream.of(postgres, redis)).join();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> "127.0.0.1:65535");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }
}
