package com.tinder.swipes.kafka.config;

import com.tinder.swipes.kafka.SwipeCreatedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * High-performance Kafka producer configuration for fire-and-forget pattern.
 * Optimized for maximum throughput with minimal latency.
 *
 * Key settings:
 * - Values are loaded from app.kafka.producer.* properties
 * - Larger batch/buffer and higher in-flight requests improve throughput
 * - acks=0 is supported for max speed when durability tradeoff is acceptable
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    @Value("${app.kafka.producer.acks:1}")
    private String acks;
    @Value("${app.kafka.producer.retries:0}")
    private int retries;
    @Value("${app.kafka.producer.enable-idempotence:false}")
    private boolean enableIdempotence;
    @Value("${app.kafka.producer.linger-ms:10}")
    private int lingerMs;
    @Value("${app.kafka.producer.batch-size:65536}")
    private int batchSize;
    @Value("${app.kafka.producer.buffer-memory:134217728}")
    private long bufferMemory;
    @Value("${app.kafka.producer.compression-type:lz4}")
    private String compressionType;
    @Value("${app.kafka.producer.max-in-flight-requests-per-connection:5}")
    private int maxInFlightRequestsPerConnection;
    @Value("${app.kafka.producer.producer-per-thread:false}")
    private boolean producerPerThread;
    @Value("${app.kafka.producer.max-block-ms:20}")
    private long maxBlockMs;
    @Value("${app.kafka.producer.delivery-timeout-ms:5000}")
    private int deliveryTimeoutMs;
    @Value("${app.kafka.producer.request-timeout-ms:3000}")
    private int requestTimeoutMs;
    @Value("${app.kafka.producer.partitioner-ignore-keys:true}")
    private boolean partitionerIgnoreKeys;

    /**
     * Producer factory with optimized settings for fire-and-forget pattern.
     */
    @Bean
    public ProducerFactory<String, SwipeCreatedEvent> swipeEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();

        // Connection settings
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Serialization
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Performance optimization settings
        props.put(ProducerConfig.ACKS_CONFIG, acks);
        props.put(ProducerConfig.RETRIES_CONFIG, retries);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, enableIdempotence);

        // Batching settings for throughput
        props.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);

        // Buffer and compression
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);

        // Parallel requests
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, maxInFlightRequestsPerConnection);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlockMs);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        props.put(ProducerConfig.PARTITIONER_IGNORE_KEYS_CONFIG, partitionerIgnoreKeys);

        // JSON serializer settings
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        DefaultKafkaProducerFactory<String, SwipeCreatedEvent> producerFactory =
                new DefaultKafkaProducerFactory<>(props);
        // Producer concurrency is controlled by thread-bound producer instances.
        producerFactory.setProducerPerThread(producerPerThread);
        return producerFactory;
    }

    /**
     * KafkaTemplate for sending SwipeCreatedEvent messages with fire-and-forget pattern.
     */
    @Bean
    public KafkaTemplate<String, SwipeCreatedEvent> kafkaTemplate() {
        return new KafkaTemplate<>(swipeEventProducerFactory());
    }
}
