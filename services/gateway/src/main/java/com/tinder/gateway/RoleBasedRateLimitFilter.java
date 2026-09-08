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
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
    public RoleBasedRateLimitFilter(
            RedisClient redisClient,
            SecurityService securityService
    ) {
        super(Config.class);
        this.proxyManager = LettuceBasedProxyManager
                .builderFor(redisClient)
                .build();
        this.securityService = securityService;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> resolveRateLimitKey(exchange)
                .flatMap(userId -> resolveRole(securityService, userId.startsWith("user:"))
                        .flatMap(role -> {
                            // Route-scoped keys so Discover deck/profile GETs cannot empty the swipe bucket.
                            String key = routeId(exchange) + ":" + userId + "-" + role;
                            RoleLimit roleLimit = config.getLimitForRole(role);

                            // A capacity of zero is how a route says "this role may not call me".
                            // It must reject outright — falling back to a default allowance would
                            // hand the role exactly the access the configuration denies it.
                            if (roleLimit.getCapacity() <= 0) {
                                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                                return exchange.getResponse().setComplete();
                            }

                            Bucket bucket = proxyManager.builder()
                                    .build(key.getBytes(), () -> getBucketConfiguration(roleLimit));

                            if (bucket.tryConsume(1)) {
                                return chain.filter(exchange);
                            }
                            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                            exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After-Seconds",
                                    String.valueOf(roleLimit.getPeriodInSeconds()));
                            return exchange.getResponse().setComplete();
                        }));
    }

    private static String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null && route.getId() != null && !route.getId().isBlank()
                ? route.getId()
                : "unknown";
    }

    /**
     * Builds the bucket key from the <em>verified</em> principal only.
     * <p>
     * Deriving it from an unverified token payload would make the limit meaningless: anyone can
     * mint a syntactically valid JWT with a fresh {@code sub} and get a brand-new bucket for
     * every request. Unauthenticated traffic is therefore keyed by IP instead.
     */
    private Mono<String> resolveRateLimitKey(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .filter(Authentication.class::isInstance)
                .cast(Authentication.class)
                .filter(this::isAuthenticated)
                .map(authentication -> {
                    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                        return "user:" + jwtAuth.getToken().getSubject();
                    }
                    return "user:" + authentication.getName();
                })
                .defaultIfEmpty("ip:" + resolveIp(exchange));
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated();
    }

    private String resolveIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getHostString() : extractKeyFromHeaders(exchange);
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

    /**
     * Authenticated callers without premium/admin use basic limits. {@code anon} is only for
     * unauthenticated/IP-keyed traffic. Keycloak tokens often omit {@code USER_BASIC}.
     */
    public static Mono<String> resolveRole(SecurityService securityService, boolean authenticated) {
        if (!authenticated) {
            return Mono.just("anon");
        }
        return securityService.isAdmin()
                .flatMap(isAdmin -> {
                    if (isAdmin) {
                        return Mono.just("admin");
                    }
                    return securityService.isPremiumUser()
                            .map(isPremium -> isPremium ? "premium" : "basic");
                });
    }

    /** @deprecated use {@link #resolveRole(SecurityService, boolean)} */
    @Deprecated
    public static Mono<String> resolveRole(SecurityService securityService) {
        return resolveRole(securityService, true);
    }

    private BucketConfiguration getBucketConfiguration(RoleLimit roleLimit) {
        int capacity = roleLimit.getCapacity();
        int periodSec = roleLimit.getPeriodInSeconds() > 0 ? roleLimit.getPeriodInSeconds() : 60;
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(capacity,
                        Refill.greedy(capacity, Duration.ofSeconds(periodSec))))
                .build();
    }

    public static class Config {
        private int adminCapacity = 1000;
        private int adminPeriodInSeconds = 3600;
        private int premiumCapacity = 500;
        private int premiumPeriodInSeconds = 3600;
        private int basicCapacity = 100;
        private int basicPeriodInSeconds = 3600;
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
