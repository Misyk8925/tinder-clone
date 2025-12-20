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

import java.net.InetSocketAddress;
import java.time.Duration;

@Component
public class RateLimitFilter extends AbstractGatewayFilterFactory<RateLimitFilter.Config> {

    private final ProxyManager<byte[]> proxyManager;

    @Autowired
    public RateLimitFilter(RedisClient redisClient) {
        super(Config.class);
        this.proxyManager = LettuceBasedProxyManager
                .builderFor(redisClient)
                // Expiration strategy is optional; use defaults for compatibility across versions
                .build();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            String key = remoteAddress != null ? remoteAddress.getHostString() : extractKeyFromHeaders(exchange);

            Bucket bucket = proxyManager.builder()
                    .build(key.getBytes(), () -> getBucketConfiguration(config));

            if (bucket.tryConsume(1)) {
                return chain.filter(exchange);
            } else {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
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

    private BucketConfiguration getBucketConfiguration(Config config) {
        int capacity = config.getCapacity() > 0 ? config.getCapacity() : 10;
        int periodSec = config.getPeriodInSeconds() > 0 ? config.getPeriodInSeconds() : 60;
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(capacity,
                        Refill.greedy(capacity, Duration.ofSeconds(periodSec))))
                .build();
    }

    public static class Config {
        private int capacity;
        private int periodInSeconds;

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getPeriodInSeconds() {
            return periodInSeconds;
        }

        public void setPeriodInSeconds(int periodInSeconds) {
            this.periodInSeconds = periodInSeconds;
        }
    }
}
