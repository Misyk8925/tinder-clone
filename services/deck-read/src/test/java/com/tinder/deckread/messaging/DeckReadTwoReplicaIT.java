package com.tinder.deckread.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Feature: two Deck Read replicas share Kafka partitions against one read model")
class DeckReadTwoReplicaIT {

    private static final String TOPIC = "swipe-saved";
    private static final String GROUP = "deck-read-two-replica-evidence";
    private static final int PARTITIONS = 6;

    @Test
    @DisplayName("Scenario: Given two Deck Read processes in one group, when six partitions are materialized, then both replicas own work and every first swipe is stored once")
    void twoProcessesSharePartitionsAndMaterializeIntoOneRedis() throws Exception {
        Path quarkusApp = Path.of("target/quarkus-app").toAbsolutePath();
        assertThat(Files.isRegularFile(quarkusApp.resolve("quarkus-run.jar")))
                .as("mvn package must build the fast-jar before the failsafe phase")
                .isTrue();

        try (Network network = Network.newNetwork();
             KafkaContainer kafka = kafka(network);
             GenericContainer<?> readModel = redis(network, "read-model");
             GenericContainer<?> deckSource = redis(network, "deck-source")) {
            Startables.deepStart(Stream.of(kafka, readModel, deckSource)).join();
            createTopic(kafka.getBootstrapServers());

            StringBuilder replicaALogs = new StringBuilder();
            StringBuilder replicaBLogs = new StringBuilder();
            try (AdminClient admin = admin(kafka.getBootstrapServers());
                 GenericContainer<?> replicaA = replica(
                    network, quarkusApp, "replica-a", replicaALogs);
                 GenericContainer<?> replicaB = replica(
                         network, quarkusApp, "replica-b", replicaBLogs)) {
                Startables.deepStart(Stream.of(replicaA, replicaB)).join();

                ConsumerGroupDescription assignments = awaitAssignments(
                        admin, Duration.ofSeconds(30));
                assertThat(assignments.members()).hasSize(2);
                assertThat(assignments.members())
                        .allSatisfy(member -> assertThat(member.assignment().topicPartitions())
                                .isNotEmpty());
                assertThat(assignments.members().stream()
                        .map(member -> member.assignment().topicPartitions())
                        .flatMap(Collection::stream)
                        .filter(partition -> TOPIC.equals(partition.topic()))
                        .collect(java.util.stream.Collectors.toSet()))
                        .hasSize(PARTITIONS);

                List<SwipeSavedEvent> events = sendOnePerPartition(
                        kafka.getBootstrapServers());
                await("all partition offsets to be acknowledged", Duration.ofSeconds(30),
                        () -> allOffsetsCommitted(admin));
                await("all first-swipe hashes to appear in the shared Redis", Duration.ofSeconds(10),
                        () -> swipeKeyCount(readModel) == PARTITIONS);

                assertThat(swipeKeyCount(readModel)).isEqualTo(PARTITIONS);
                for (SwipeSavedEvent event : events) {
                    String key = "dr:viewer:{" + event.profile1Id() + "}:swipes";
                    var result = readModel.execInContainer(
                            "redis-cli", "HGET", key, event.profile2Id());
                    assertThat(result.getExitCode()).isZero();
                    assertThat(result.getStdout().trim()).isEqualTo(
                            event.eventId() + "|" + event.timestamp() + "|" + event.decision());
                }
            } catch (Throwable failure) {
                throw new AssertionError(
                        "Two-replica evidence failed. replica-a logs:\n" + replicaALogs
                                + "\nreplica-b logs:\n" + replicaBLogs,
                        failure);
            }
        }
    }

    private KafkaContainer kafka(Network network) {
        return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                .withNetwork(network)
                .withNetworkAliases("kafka")
                .withListener(() -> "kafka:19092");
    }

    private GenericContainer<?> redis(Network network, String alias) {
        return new GenericContainer<>(DockerImageName.parse("redis:8.2.1-alpine"))
                .withNetwork(network)
                .withNetworkAliases(alias)
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort());
    }

    private GenericContainer<?> replica(
            Network network,
            Path quarkusApp,
            String alias,
            StringBuilder logs
    ) {
        return new GenericContainer<>(DockerImageName.parse("eclipse-temurin:21-jre-alpine"))
                .withNetwork(network)
                .withNetworkAliases(alias)
                .withCopyFileToContainer(MountableFile.forHostPath(quarkusApp), "/app")
                .withEnv("KAFKA_BOOTSTRAP_SERVERS", "kafka:19092")
                .withEnv("DECK_READ_SWIPE_GROUP_ID", GROUP)
                .withEnv("DECK_READ_REDIS_HOSTS", "redis://read-model:6379")
                .withEnv("DECK_SOURCE_REDIS_HOST", "deck-source")
                .withEnv("DECK_SOURCE_REDIS_PORT", "6379")
                .withEnv("DECK_READ_CURSOR_SECRET", "two-replica-runtime-evidence-secret")
                .withEnv("DECK_BASE_URL", "http://unused-deck:8030")
                .withCommand(
                        "java",
                        "-Dquarkus.redis.read-model.client-type=standalone",
                        "-Dmp.messaging.incoming.profile-deck-card-projection.enabled=false",
                        "-Dmp.messaging.incoming.match-created.enabled=false",
                        "-Dquarkus.http.port=8040",
                        "-jar", "/app/quarkus-run.jar")
                .withLogConsumer(frame -> appendLog(logs, frame))
                .waitingFor(Wait.forLogMessage(".*deck-read 1\\.0\\.0.*started.*\\n", 1))
                .withStartupTimeout(Duration.ofSeconds(90));
    }

    private void appendLog(StringBuilder logs, OutputFrame frame) {
        synchronized (logs) {
            logs.append(frame.getUtf8String());
        }
    }

    private void createTopic(String bootstrapServers) throws Exception {
        try (AdminClient admin = admin(bootstrapServers)) {
            admin.createTopics(List.of(new NewTopic(TOPIC, PARTITIONS, (short) 1)))
                    .all().get(10, TimeUnit.SECONDS);
        }
    }

    private ConsumerGroupDescription awaitAssignments(
            AdminClient admin,
            Duration timeout
    ) throws InterruptedException {
        ConsumerGroupDescription[] current = new ConsumerGroupDescription[1];
        await("two non-empty consumer assignments", timeout, () -> {
            try {
                current[0] = admin.describeConsumerGroups(List.of(GROUP))
                        .all().get(5, TimeUnit.SECONDS).get(GROUP);
                return current[0] != null
                        && current[0].members().size() == 2
                        && current[0].members().stream()
                        .allMatch(member -> !member.assignment().topicPartitions().isEmpty());
            } catch (Exception notReady) {
                return false;
            }
        });
        return current[0];
    }

    private List<SwipeSavedEvent> sendOnePerPartition(String bootstrapServers) throws Exception {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        ObjectMapper mapper = new ObjectMapper();
        List<SwipeSavedEvent> events = IntStream.range(0, PARTITIONS)
                .mapToObj(partition -> new SwipeSavedEvent(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        partition % 2 == 0,
                        Instant.now().plusMillis(partition).toEpochMilli()))
                .toList();
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            for (int partition = 0; partition < events.size(); partition++) {
                SwipeSavedEvent event = events.get(partition);
                producer.send(new ProducerRecord<>(
                                TOPIC, partition, event.profile1Id(), mapper.writeValueAsString(event)))
                        .get(10, TimeUnit.SECONDS);
            }
            producer.flush();
        }
        return events;
    }

    private boolean allOffsetsCommitted(AdminClient admin) {
        try {
            Map<TopicPartition, OffsetAndMetadata> offsets = admin
                    .listConsumerGroupOffsets(GROUP)
                    .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
            if (offsets.size() != PARTITIONS) {
                return false;
            }
            return IntStream.range(0, PARTITIONS)
                    .allMatch(partition -> {
                        OffsetAndMetadata committed = offsets.get(new TopicPartition(TOPIC, partition));
                        return committed != null && committed.offset() >= 1;
                    });
        } catch (Exception notCommittedYet) {
            return false;
        }
    }

    private long swipeKeyCount(GenericContainer<?> readModel) {
        try {
            var result = readModel.execInContainer(
                    "redis-cli", "--scan", "--pattern", "dr:viewer:*:swipes");
            if (result.getExitCode() != 0 || result.getStdout().isBlank()) {
                return 0;
            }
            return result.getStdout().lines().filter(line -> !line.isBlank()).count();
        } catch (Exception unavailable) {
            return 0;
        }
    }

    private AdminClient admin(String bootstrapServers) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return AdminClient.create(properties);
    }

    private void await(String description, Duration timeout, BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }
}
