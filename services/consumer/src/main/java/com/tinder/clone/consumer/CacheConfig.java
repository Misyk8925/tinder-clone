package com.tinder.clone.consumer;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SWIPE_EXISTS_CACHE = "swipeExists";
    public static final String SWIPE_RECORD_CACHE = "swipeRecord";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                )
                .disableCachingNullValues();

        // Configuration for swipe existence checks - no TTL (swipes don't disappear)
        RedisCacheConfiguration swipeExistsConfig = defaultConfig
                .entryTtl(Duration.ZERO); // No expiration

        // Configuration for swipe records - 1 hour TTL
        RedisCacheConfiguration swipeRecordConfig = defaultConfig
                .entryTtl(Duration.ofHours(1));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(SWIPE_EXISTS_CACHE, swipeExistsConfig)
                .withCacheConfiguration(SWIPE_RECORD_CACHE, swipeRecordConfig)
                .build();
    }
}