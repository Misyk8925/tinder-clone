package com.tinder.profiles.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;

import java.time.Duration;

/**
 * Configuration for Bucket4j rate limiting with Redis backend.
 * Provides the LettuceBasedProxyManager required by bucket4j-spring-boot-starter.
 */
@Configuration
public class Bucket4jConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Creates a Redis client for Bucket4j.
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient bucket4jRedisClient() {
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            uriBuilder.withPassword(redisPassword.toCharArray());
        }

        return RedisClient.create(uriBuilder.build());
    }

    /**
     * Creates a stateful Redis connection for Bucket4j.
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> bucket4jRedisConnection(RedisClient bucket4jRedisClient) {
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return bucket4jRedisClient.connect(codec);
    }

    /**
     * Creates the LettuceBasedProxyManager for distributed rate limiting.
     * This bean is auto-detected by bucket4j-spring-boot-starter when cache-to-use is redis-lettuce.
     */
    @Bean
    public LettuceBasedProxyManager<String> lettuceBasedProxyManager(
            StatefulRedisConnection<String, byte[]> bucket4jRedisConnection) {
        return LettuceBasedProxyManager.builderFor(bucket4jRedisConnection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofDays(1)))
                .build();
    }
}
