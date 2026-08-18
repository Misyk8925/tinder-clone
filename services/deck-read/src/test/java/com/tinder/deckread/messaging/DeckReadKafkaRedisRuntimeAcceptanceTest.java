package com.tinder.deckread.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.contracts.event.v1.DeckCardPreferences;
import com.tinder.contracts.event.v1.DeckCardProjection;
import com.tinder.contracts.event.v1.ProfileDeckCardProjectionEvent;
import com.tinder.contracts.event.v1.ProfileProjectionOperation;
import com.tinder.contracts.event.v1.ProjectionSource;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ReadModelKeys;
import com.tinder.deckread.readmodel.ReadModelReadiness;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(DeckReadKafkaRedisRuntimeAcceptanceTest.KafkaRuntimeProfile.class)
@QuarkusTestResource(
        value = DeckReadKafkaRedisRuntimeAcceptanceTest.KafkaTestResource.class,
        restrictToAnnotatedClass = true)
@Tag("acceptance")
@DisplayName("Feature: Deck Read materializes at-least-once Kafka delivery and sanitizes real DLT records")
class DeckReadKafkaRedisRuntimeAcceptanceTest {

    private static final String INPUT_TOPIC = "swipe-saved";
    private static final String DLT_TOPIC = "swipe-saved.dlt";
    private static final String PROFILE_TOPIC = "profile.deck-card-projection.v1";
    private static final String MATERIALIZER_GROUP = "deck-read-swipe-runtime-evidence";
    private static final String PROFILE_MATERIALIZER_GROUP = "deck-read-profile-runtime-evidence";

    @Inject
    @RedisClientName("read-model")
    RedisDataSource redis;

    @Inject
    ObjectMapper mapper;

    @Inject
    ProfileProjectionStore profiles;

    @Inject
    ReadModelReadiness readiness;

    @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "localhost:1")
    String bootstrapServers;

    @BeforeEach
    void flushReadModel() {
        redis.flushall();
    }

    @Test
    @DisplayName("Scenario: Given the same swipe record is delivered twice, when both offsets are acknowledged, then Redis keeps one first decision and one repeat candidate")
    void duplicateDeliveryIsAcknowledgedAndMaterializedOnce() throws Exception {
        // Given
        UUID viewer = UUID.randomUUID();
        UUID candidate = UUID.randomUUID();
        SwipeSavedEvent event = new SwipeSavedEvent(
                UUID.randomUUID().toString(), viewer.toString(), candidate.toString(),
                true, Instant.now().toEpochMilli());
        String payload = mapper.writeValueAsString(event);

        // When
        RecordMetadata secondDelivery;
        try (KafkaProducer<String, String> producer = producer()) {
            producer.send(new ProducerRecord<>(INPUT_TOPIC, viewer.toString(), payload))
                    .get(10, TimeUnit.SECONDS);
            secondDelivery = producer.send(new ProducerRecord<>(INPUT_TOPIC, viewer.toString(), payload))
                    .get(10, TimeUnit.SECONDS);
            producer.flush();
        }
        TopicPartition partition = new TopicPartition(INPUT_TOPIC, secondDelivery.partition());
        await("both duplicate Kafka offsets to be acknowledged", Duration.ofSeconds(20),
                () -> committedOffset(partition) >= secondDelivery.offset() + 1);

        // Then
        Map<String, String> firstDecisions = redis.hash(String.class)
                .hgetall(ReadModelKeys.swipes(viewer));
        assertThat(firstDecisions)
                .containsOnlyKeys(candidate.toString());
        assertThat(firstDecisions.get(candidate.toString()))
                .isEqualTo(event.eventId() + "|" + event.timestamp() + "|" + event.decision());
        assertThat(redis.sortedSet(String.class)
                .zcard(ReadModelKeys.repeatCandidates(viewer))).isEqualTo(1);
    }

    @Test
    @DisplayName("Scenario: Given a valid Kafka event that cannot be materialized, when bounded retries are exhausted, then the real DLT contains sanitized replay metadata")
    void persistentMaterializationFailureProducesSanitizedDltRecord() throws Exception {
        // Given
        String eventId = UUID.randomUUID().toString();
        String candidateId = UUID.randomUUID().toString();
        SwipeSavedEvent invalid = new SwipeSavedEvent(
                eventId, "not-a-viewer-uuid", candidateId, true, Instant.now().toEpochMilli());

        // When
        try (KafkaProducer<String, String> producer = producer()) {
            producer.send(new ProducerRecord<>(
                            INPUT_TOPIC, invalid.profile1Id(), mapper.writeValueAsString(invalid)))
                    .get(10, TimeUnit.SECONDS);
            producer.flush();
        }
        ConsumerRecord<String, byte[]> dltRecord = awaitDlt(eventId, Duration.ofSeconds(20));
        JsonNode dlt = mapper.readTree(dltRecord.value());

        // Then
        assertThat(dlt.get("payloadType").asText()).isEqualTo("swipe.saved");
        assertThat(dlt.get("eventId").asText()).isEqualTo(eventId);
        assertThat(dlt.get("viewerProfileId").asText()).isEqualTo("not-a-viewer-uuid");
        assertThat(dlt.get("candidateProfileId").asText()).isEqualTo(candidateId);
        assertThat(dlt.has("decision")).isFalse();
        assertThat(dlt.has("timestamp")).isFalse();
    }

    @Test
    @DisplayName("Scenario: Given a lost Read Cluster and interrupted backfill, when the same runId resumes, then count and lag checks precede READY")
    void sameRunIdRecoveryRequiresCompleteProjectionAndZeroLagBeforeReady() throws Exception {
        // Given: the rebuilt Redis starts gated and the first backfill page is consumed.
        UUID runId = UUID.randomUUID();
        ProfileDeckCardProjectionEvent first = profileBackfillEvent(UUID.randomUUID(), runId, 1);
        assertThat(readiness.isReady().await().indefinitely()).isFalse();
        RecordMetadata firstPage = sendProfile(first);
        TopicPartition partition = new TopicPartition(PROFILE_TOPIC, firstPage.partition());
        await("the first backfill page to be materialized", Duration.ofSeconds(20),
                () -> committedOffset(PROFILE_MATERIALIZER_GROUP, partition) >= firstPage.offset() + 1);

        // When: recovery resumes with the same durable runId. The first event is
        // redelivered at least once, followed by the next page's event.
        ProfileDeckCardProjectionEvent second = profileBackfillEvent(UUID.randomUUID(), runId, 1);
        RecordMetadata lastDelivery;
        try (KafkaProducer<String, String> producer = producer()) {
            producer.send(new ProducerRecord<>(
                            PROFILE_TOPIC, first.profileId().toString(), mapper.writeValueAsString(first)))
                    .get(10, TimeUnit.SECONDS);
            lastDelivery = producer.send(new ProducerRecord<>(
                            PROFILE_TOPIC, second.profileId().toString(), mapper.writeValueAsString(second)))
                    .get(10, TimeUnit.SECONDS);
            producer.flush();
        }
        await("the resumed backfill records to be acknowledged", Duration.ofSeconds(20),
                () -> committedOffset(PROFILE_MATERIALIZER_GROUP, partition) >= lastDelivery.offset() + 1);

        // Then: duplicate delivery did not create a third projection, both
        // distinct cards and user mappings exist, and Kafka lag is zero.
        assertThat(profiles.card(first.profileId()).await().indefinitely()).isPresent();
        assertThat(profiles.card(second.profileId()).await().indefinitely()).isPresent();
        assertThat(profiles.viewerProfileId(first.userId()).await().indefinitely())
                .isEqualTo(first.profileId());
        assertThat(profiles.viewerProfileId(second.userId()).await().indefinitely())
                .isEqualTo(second.profileId());
        // Profile events also create viewer-local materialization-request metadata.
        // Count the projection contract explicitly instead of coupling recovery to
        // the number of internal read-model keys.
        assertThat(redis.execute("KEYS", "dr:profile:*:card").size()).isEqualTo(2);
        assertThat(redis.execute("KEYS", "dr:user:*:profile").size()).isEqualTo(2);
        assertThat(endOffset(partition) - committedOffset(PROFILE_MATERIALIZER_GROUP, partition))
                .isZero();

        // READY belongs to the operator step after those checks; repeat data has
        // its own gate and cannot become available accidentally.
        assertThat(readiness.isReady().await().indefinitely()).isFalse();
        readiness.markReady().await().indefinitely();
        assertThat(readiness.isReady().await().indefinitely()).isTrue();
        assertThat(readiness.isRepeatReady().await().indefinitely()).isFalse();
        readiness.markRepeatReady().await().indefinitely();
        assertThat(readiness.isRepeatReady().await().indefinitely()).isTrue();
    }

    private ProfileDeckCardProjectionEvent profileBackfillEvent(
            UUID profileId,
            UUID runId,
            long version
    ) {
        return new ProfileDeckCardProjectionEvent(
                UUID.randomUUID(), profileId, "user-" + profileId, version, Instant.now(),
                ProfileProjectionOperation.UPSERT, ProjectionSource.BACKFILL, runId,
                new DeckCardProjection(
                        profileId, "profile-" + profileId, 27, "Vienna", "recovered", true,
                        new DeckCardPreferences(18, 40, "ALL", 50), List.of(), List.of()));
    }

    private RecordMetadata sendProfile(ProfileDeckCardProjectionEvent event) throws Exception {
        try (KafkaProducer<String, String> producer = producer()) {
            RecordMetadata metadata = producer.send(new ProducerRecord<>(
                            PROFILE_TOPIC, event.profileId().toString(), mapper.writeValueAsString(event)))
                    .get(10, TimeUnit.SECONDS);
            producer.flush();
            return metadata;
        }
    }

    private KafkaProducer<String, String> producer() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(properties);
    }

    private long committedOffset(TopicPartition partition) {
        return committedOffset(MATERIALIZER_GROUP, partition);
    }

    private long committedOffset(String group, TopicPartition partition) {
        Map<String, Object> properties = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient admin = AdminClient.create(properties)) {
            var offsets = admin.listConsumerGroupOffsets(group)
                    .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
            var committed = offsets.get(partition);
            return committed == null ? -1 : committed.offset();
        } catch (Exception notCommittedYet) {
            return -1;
        }
    }

    private long endOffset(TopicPartition partition) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "deck-read-end-offset-evidence-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(properties)) {
            consumer.assign(List.of(partition));
            consumer.seekToEnd(List.of(partition));
            return consumer.position(partition);
        }
    }

    private ConsumerRecord<String, byte[]> awaitDlt(String eventId, Duration timeout) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "deck-read-dlt-evidence-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(java.util.List.of(DLT_TOPIC));
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(250))) {
                    try {
                        JsonNode value = mapper.readTree(record.value());
                        if (eventId.equals(value.path("eventId").asText())) {
                            return record;
                        }
                    } catch (Exception ignored) {
                        // Keep polling so the assertion reports a missing sanitized record,
                        // rather than failing on an unrelated broker record.
                    }
                }
            }
        }
        throw new AssertionError("Timed out waiting for sanitized DLT record " + eventId);
    }

    private void await(String description, Duration timeout, BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    public static final class KafkaRuntimeProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> config = new HashMap<>();
            config.put("quarkus.kafka.devservices.enabled", "false");
            config.put("mp.messaging.incoming.swipe-saved.enabled", "true");
            config.put("mp.messaging.incoming.swipe-saved.group.id", MATERIALIZER_GROUP);
            config.put("mp.messaging.incoming.profile-deck-card-projection.enabled", "true");
            config.put("mp.messaging.incoming.profile-deck-card-projection.group.id", PROFILE_MATERIALIZER_GROUP);
            config.put("mp.messaging.outgoing.materialization-requests-out.enabled", "true");
            config.put("mp.messaging.incoming.match-created.enabled", "false");
            config.put("deck-read.read-model.require-ready-marker", "true");
            return Map.copyOf(config);
        }
    }

    public static final class KafkaTestResource implements QuarkusTestResourceLifecycleManager {
        private KafkaContainer kafka;

        @Override
        public Map<String, String> start() {
            kafka = new KafkaContainer(
                    DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
            kafka.start();
            return Map.of("kafka.bootstrap.servers", kafka.getBootstrapServers());
        }

        @Override
        public void stop() {
            if (kafka != null) {
                kafka.stop();
            }
        }
    }
}
