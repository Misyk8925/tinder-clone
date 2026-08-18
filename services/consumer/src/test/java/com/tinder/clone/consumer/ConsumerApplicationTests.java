package com.tinder.clone.consumer;

import org.junit.jupiter.api.Test;

/**
 * Given CI has Docker, Testcontainers, and Maven-generated mTLS keystores,
 * When the Spring context loads,
 * Then it does not require laptop Postgres or gitignored production certs.
 */
class ConsumerApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
