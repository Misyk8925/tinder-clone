package com.tinder.clone.moderation.config

import com.tinder.clone.moderation.infrastructure.messaging.EventPublisher
import com.tinder.clone.moderation.infrastructure.messaging.ModerationCommandDlq
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff
import tools.jackson.databind.ObjectMapper
import java.time.Clock

@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "moderation.kafka", name = ["enabled"], havingValue = "true")
class ModerationKafkaConfiguration(
    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}") private val bootstrapServers: String,
    @Value("\${moderation.kafka.group-id:moderation-service}") private val groupId: String
) {
    @Bean
    fun moderationProducerFactory(): ProducerFactory<String, String> =
        DefaultKafkaProducerFactory(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.ACKS_CONFIG to "all"
            )
        )

    @Bean
    fun kafkaTemplate(factory: ProducerFactory<String, String>): KafkaTemplate<String, String> =
        KafkaTemplate(factory)

    @Bean
    fun kafkaEventPublisher(kafkaTemplate: KafkaTemplate<String, String>): EventPublisher =
        EventPublisher { topic, key, payload -> kafkaTemplate.send(topic, key, payload).get() }

    @Bean
    fun moderationCommandDlq(
        publisher: EventPublisher,
        objectMapper: ObjectMapper,
        kafka: ModerationKafkaProperties,
        clock: Clock
    ) = ModerationCommandDlq(publisher, objectMapper, kafka, clock)

    @Bean
    fun moderationConsumerFactory(): ConsumerFactory<String, String> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to groupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest"
            )
        )

    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        dlq: ModerationCommandDlq
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.setConsumerFactory(consumerFactory)
        // FixedBackOff maxAttempts is additional retries after the first delivery.
        factory.setCommonErrorHandler(
            DefaultErrorHandler(
                { record, exception ->
                    dlq.publish(record.value()?.toString().orEmpty(), exception, ModerationCommandDlq.MAX_ATTEMPTS)
                },
                FixedBackOff(100L, (ModerationCommandDlq.MAX_ATTEMPTS - 1).toLong())
            )
        )
        return factory
    }
}
