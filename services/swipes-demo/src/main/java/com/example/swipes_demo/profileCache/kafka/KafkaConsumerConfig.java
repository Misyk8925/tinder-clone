package com.example.swipes_demo.profileCache.kafka;

import com.example.swipes_demo.profileCache.ProfileDeleteEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private String groupId = "swipes-service";

    @Value("${swipes.kafka.error-handler.max-retries:5}")
    private long maxRetries;

    @Value("${swipes.kafka.error-handler.backoff-ms:1000}")
    private long retryBackoffMs;

    @Value("${kafka.topics.profile-created}")
    private String profileCreatedTopic;

    @Value("${kafka.topics.profile-deleted}")
    private String profileDeletedTopic;

    @Bean
    public NewTopic profileCreatedDeadLetterTopic() {
        return deadLetterTopic(profileCreatedTopic + ".dlt");
    }

    @Bean
    public NewTopic profileDeletedDeadLetterTopic() {
        return deadLetterTopic(profileDeletedTopic + ".dlt");
    }

    @Bean
    public DefaultErrorHandler profileKafkaErrorHandler(
            @Qualifier("profileDeadLetterKafkaTemplate") KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".dlt", -1)
        );
        recoverer.setFailIfSendResultIsError(true);
        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(Math.max(0, retryBackoffMs), Math.max(0, maxRetries))
        );
    }

    /**
     * Consumer factory for ProfileCreateEvent deserialization
     */
    @Bean
    public ConsumerFactory<String, ProfileCreateEvent> profileCreateEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-profile");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        // Error handling deserializer wrapper
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        // Delegate deserializers
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);

        // JSON deserializer configuration
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.example.swipes_demo.*");
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, ProfileCreateEvent.class.getName());
        props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProfileCreateEvent> profileCreateEventKafkaListenerContainerFactory(
            ConsumerFactory<String, ProfileCreateEvent> profileCreateEventConsumerFactory,
            DefaultErrorHandler profileKafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, ProfileCreateEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(profileCreateEventConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(profileKafkaErrorHandler);
        return factory;
    }

    /**
     * Consumer factory for ProfileDeleteEvent deserialization
     */
    @Bean
    public ConsumerFactory<String, ProfileDeleteEvent> profileDeleteEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-profile-delete");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        // Error handling deserializer wrapper
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        // Delegate deserializers
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);

        // JSON deserializer configuration
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.example.swipes_demo.*");
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, ProfileDeleteEvent.class.getName());
        props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProfileDeleteEvent> profileDeleteEventKafkaListenerContainerFactory(
            ConsumerFactory<String, ProfileDeleteEvent> profileDeleteEventConsumerFactory,
            DefaultErrorHandler profileKafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, ProfileDeleteEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(profileDeleteEventConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(profileKafkaErrorHandler);
        return factory;
    }

    private NewTopic deadLetterTopic(String name) {
        return TopicBuilder.name(name)
                .partitions(10)
                .replicas(1)
                .config("retention.ms", "1209600000")
                .config("cleanup.policy", "delete")
                .build();
    }
}
