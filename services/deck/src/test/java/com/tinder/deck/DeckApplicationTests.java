package com.tinder.deck;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Given CI has Docker and Maven-generated mTLS keystores,
 * When the Spring context loads,
 * Then it uses Testcontainers Redis and builds the mTLS HttpClient from test certs.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class DeckApplicationTests {

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.2.1-alpine"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redis(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("spring.kafka.bootstrap-servers", () -> "127.0.0.1:65535");
	}

	@Test
	void contextLoads() {
	}

}
