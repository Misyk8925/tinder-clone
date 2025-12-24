package com.tinder.gateway;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;

@Component
public class RoleBasedRateLimitFilter extends AbstractGatewayFilterFactory<RoleBasedRateLimitFilter.Config> {

    private final ProxyManager<byte[]> proxyManager;
    private final SecurityService securityService;

    @Autowired
    public RoleBasedRateLimitFilter(RedisClient redisClient, SecurityService securityService) {
        super(Config.class);
        this.proxyManager = LettuceBasedProxyManager
                .builderFor(redisClient)
                .build();
        this.securityService = securityService;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            String userId = remoteAddress != null ? remoteAddress.getHostString() : extractKeyFromHeaders(exchange);

            // Get user role from SecurityService reactively using RoleResolver
            return resolveRole(securityService)
                    .flatMap(role -> {
                        // Create unique key with role
                        String key = userId + "-" + role;

                        // Get appropriate limits based on role
                        RoleLimit roleLimit = config.getLimitForRole(role);

                        Bucket bucket = proxyManager.builder()
                                .build(key.getBytes(), () -> getBucketConfiguration(roleLimit));

                        if (bucket.tryConsume(1)) {
                            return chain.filter(exchange);
                        } else {
                            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                            exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After-Seconds",
                                String.valueOf(roleLimit.getPeriodInSeconds()));
                            return exchange.getResponse().setComplete();
                        }
                    });
        };
    }

    private String extractKeyFromHeaders(ServerWebExchange exchange) {
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (realIp != null && !realIp.isBlank()) {
            String[] ips = realIp.split(",");
            if (ips.length > 0)
                return ips[0].trim();
        }

        realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return "unknown";
    }

    public static Mono<String> resolveRole(SecurityService securityService) {
        return securityService.isAdmin()
                .flatMap(isAdmin -> {
                    if (isAdmin) {
                        return Mono.just("admin");
                    }
                    return securityService.isPremiumUser()
                            .flatMap(isPremium -> {
                                if (isPremium) {
                                    return Mono.just("premium");
                                }
                                return securityService.isBasicUser()
                                        .map(isBasic -> isBasic ? "basic" : "anon");
                            });
                });
    }

    private BucketConfiguration getBucketConfiguration(RoleLimit roleLimit) {
        int capacity = roleLimit.getCapacity() > 0 ? roleLimit.getCapacity() : 10;
        int periodSec = roleLimit.getPeriodInSeconds() > 0 ? roleLimit.getPeriodInSeconds() : 60;
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(capacity,
                        Refill.greedy(capacity, Duration.ofSeconds(periodSec))))
                .build();
    }

    public static class Config {
        // Admin limits
        private int adminCapacity = 1000;
        private int adminPeriodInSeconds = 3600;

        // Premium limits
        private int premiumCapacity = 500;
        private int premiumPeriodInSeconds = 3600;

        // Basic limits
        private int basicCapacity = 100;
        private int basicPeriodInSeconds = 3600;

        // Anonymous limits
        private int anonCapacity = 50;
        private int anonPeriodInSeconds = 3600;

        public RoleLimit getLimitForRole(String role) {
            return switch (role) {
                case "admin" -> new RoleLimit(adminCapacity, adminPeriodInSeconds);
                case "premium" -> new RoleLimit(premiumCapacity, premiumPeriodInSeconds);
                case "basic" -> new RoleLimit(basicCapacity, basicPeriodInSeconds);
                default -> new RoleLimit(anonCapacity, anonPeriodInSeconds);
            };
        }

        // Getters and setters for configuration via YAML
        public int getAdminCapacity() { return adminCapacity; }
        public void setAdminCapacity(int adminCapacity) { this.adminCapacity = adminCapacity; }

        public int getAdminPeriodInSeconds() { return adminPeriodInSeconds; }
        public void setAdminPeriodInSeconds(int adminPeriodInSeconds) { this.adminPeriodInSeconds = adminPeriodInSeconds; }

        public int getPremiumCapacity() { return premiumCapacity; }
        public void setPremiumCapacity(int premiumCapacity) { this.premiumCapacity = premiumCapacity; }

        public int getPremiumPeriodInSeconds() { return premiumPeriodInSeconds; }
        public void setPremiumPeriodInSeconds(int premiumPeriodInSeconds) { this.premiumPeriodInSeconds = premiumPeriodInSeconds; }

        public int getBasicCapacity() { return basicCapacity; }
        public void setBasicCapacity(int basicCapacity) { this.basicCapacity = basicCapacity; }

        public int getBasicPeriodInSeconds() { return basicPeriodInSeconds; }
        public void setBasicPeriodInSeconds(int basicPeriodInSeconds) { this.basicPeriodInSeconds = basicPeriodInSeconds; }

        public int getAnonCapacity() { return anonCapacity; }
        public void setAnonCapacity(int anonCapacity) { this.anonCapacity = anonCapacity; }

        public int getAnonPeriodInSeconds() { return anonPeriodInSeconds; }
        public void setAnonPeriodInSeconds(int anonPeriodInSeconds) { this.anonPeriodInSeconds = anonPeriodInSeconds; }
    }

    public static class RoleLimit {
        private final int capacity;
        private final int periodInSeconds;

        public RoleLimit(int capacity, int periodInSeconds) {
            this.capacity = capacity;
            this.periodInSeconds = periodInSeconds;
        }

        public int getCapacity() {
            return capacity;
        }

        public int getPeriodInSeconds() {
            return periodInSeconds;
        }
    }
}

