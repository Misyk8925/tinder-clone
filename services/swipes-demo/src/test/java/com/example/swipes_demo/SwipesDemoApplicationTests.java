package com.example.swipes_demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Given CI has Docker but no laptop Postgres/Redis,
 * When the Spring context loads,
 * Then it uses Testcontainers and does not open 127.0.0.1:54335.
 */
@SpringBootTest(properties = {
		"spring.kafka.listener.auto-startup=false",
		"spring.docker.compose.skip.in-tests=true"
})
@Testcontainers
class SwipesDemoApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("swipes_test")
			.withUsername("test")
			.withPassword("test");

	@Container
	@ServiceConnection(name = "redis")
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void kafka(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.bootstrap-servers", () -> "127.0.0.1:65535");
	}

	@Test
	void contextLoads() {
	}

}
