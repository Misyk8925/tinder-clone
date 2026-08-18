package com.tinder.clone.consumer.kafka.config;

import com.tinder.clone.consumer.kafka.event.ProfileCreateEvent;
import com.tinder.clone.consumer.kafka.event.ProfileDeleteEvent;
import com.tinder.clone.consumer.kafka.event.SwipeCreatedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
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
@EnableKafka
public class KafkaConfig {

    @Value("${app.kafka.topic.swipe-created}")
    private String swipeCreatedTopic;

    @Value("${app.kafka.topic.profile-created}")
    private String profileCreatedTopic;

    @Value("${app.kafka.topic.profile-deleted}")
    private String profileDeletedTopic;

    @Value("${app.kafka.topic.match-created}")
    private String matchCreatedTopic;

    @Value("${app.kafka.topic.swipe-saved}")
    private String swipeSavedTopic;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.listener.concurrency:1}")
    private int concurrency;

    @Value("${app.kafka.error-handler.max-retries:5}")
    private long maxRetries;

    @Value("${app.kafka.error-handler.backoff-ms:1000}")
    private long retryBackoffMs;

    @Bean
    public NewTopic swipeCreatedTopic() {
        return TopicBuilder.name(swipeCreatedTopic)
                .partitions(10)
                .replicas(1)
                .config("retention.ms", "604800000")
                .config("cleanup.policy", "delete")
                .build();
    }

    @Bean
    public NewTopic profileCreatedTopic() {
        return TopicBuilder.name(profileCreatedTopic)
                .partitions(10)
                .replicas(1)
                .config("retention.ms", "604800000")
                .config("cleanup.policy", "delete")
                .build();
    }

    @Bean
    public NewTopic profileDeletedTopic() {
        return TopicBuilder.name(profileDeletedTopic)
                .partitions(10)
                .replicas(1)
                .config("retention.ms", "604800000")
                .config("cleanup.policy", "delete")
                .build();
    }

    @Bean
    public NewTopic matchCreatedTopic() {
        return TopicBuilder.name(matchCreatedTopic)
                .partitions(10)
                .replicas(1)
                .config("retention.ms", "604800000")
                .config("cleanup.policy", "delete")
                .build();
    }

    @Bean
    public NewTopic swipeSavedTopic() {
        return TopicBuilder.name(swipeSavedTopic)
                .partitions(10)
                .replicas(1)
                .config("retention.ms", "604800000")
                .config("cleanup.policy", "delete")
                .build();
    }

    @Bean
    public NewTopic swipeCreatedDeadLetterTopic() {
        return deadLetterTopic(swipeCreatedTopic + ".dlt");
    }

    @Bean
    public NewTopic profileCreatedDeadLetterTopic() {
        return deadLetterTopic(profileCreatedTopic + ".dlt");
    }

    @Bean
    public NewTopic profileDeletedDeadLetterTopic() {
        return deadLetterTopic(profileDeletedTopic + ".dlt");
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".dlt", -1)
        );
        recoverer.setFailIfSendResultIsError(true);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(Math.max(0, retryBackoffMs), Math.max(0, maxRetries))
        );
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    @Bean
    public ConsumerFactory<String, SwipeCreatedEvent> swipeEventConsumerFactory() {
        Map<String, Object> props = baseConsumerProps(groupId, SwipeCreatedEvent.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConsumerFactory<String, ProfileCreateEvent> profileEventConsumerFactory() {
        Map<String, Object> props = baseConsumerProps(groupId + "-profile", ProfileCreateEvent.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConsumerFactory<String, ProfileDeleteEvent> profileDeleteEventConsumerFactory() {
        Map<String, Object> props = baseConsumerProps(groupId + "-profile-delete", ProfileDeleteEvent.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SwipeCreatedEvent> kafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, SwipeCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(swipeEventConsumerFactory());
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProfileCreateEvent> profileKafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ProfileCreateEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(profileEventConsumerFactory());
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProfileDeleteEvent> profileDeleteKafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ProfileDeleteEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(profileDeleteEventConsumerFactory());
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    private Map<String, Object> baseConsumerProps(String kafkaGroupId, Class<?> valueType) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaGroupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.tinder.*");
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, valueType.getName());
        props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return props;
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
