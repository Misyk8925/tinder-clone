package com.tinder.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

import java.util.Objects;

@SpringBootApplication
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

	@Bean
	public KeyResolver keyResolver(SecurityService securityService) {
		return exchange -> {
			String hostName = Objects.requireNonNull(exchange
							.getRequest()
							.getRemoteAddress())
					.getHostName();

			return RoleBasedRateLimitFilter.resolveRole(securityService)
					.map(roleKey -> hostName + "-" + roleKey);
		};
	}
}
