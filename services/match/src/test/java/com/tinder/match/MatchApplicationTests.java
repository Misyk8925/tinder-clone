package com.tinder.match;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Given CI has Docker but no laptop Postgres or Compose,
 * When the Spring context loads,
 * Then it uses a Testcontainers Postgres and does not open 127.0.0.1:54332.
 */
@SpringBootTest(properties = {
		"spring.docker.compose.skip.in-tests=true",
		"spring.kafka.listener.auto-startup=false"
})
@Testcontainers
class MatchApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("match_test")
			.withUsername("test")
			.withPassword("test");

	@DynamicPropertySource
	static void kafka(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.bootstrap-servers", () -> "127.0.0.1:65535");
	}

	@Test
	void contextLoads() {
	}

}
