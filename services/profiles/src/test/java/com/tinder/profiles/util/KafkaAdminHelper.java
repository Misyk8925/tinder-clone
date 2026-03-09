package com.tinder.profiles.util;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Helper for Kafka admin operations used in integration tests.
 * Opens a single AdminClient per method call to avoid boilerplate.
 */
public class KafkaAdminHelper {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminHelper.class);

    private final String bootstrapServers;

    public KafkaAdminHelper(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public boolean isConsumerGroupRegistered(String consumerGroupId) {
        try (AdminClient admin = createAdminClient()) {
            Collection<ConsumerGroupListing> groups = admin
                    .listConsumerGroups()
                    .all()
                    .get(10, TimeUnit.SECONDS);
            return groups.stream().anyMatch(g -> consumerGroupId.equals(g.groupId()));
        } catch (Exception e) {
            log.warn("Could not list consumer groups while checking {}: {}", consumerGroupId, e.getMessage());
            return false;
        }
    }

    public boolean isConsumerGroupActive(String consumerGroupId) {
        try (AdminClient admin = createAdminClient()) {
            Map<String, ConsumerGroupDescription> descriptions = admin
                    .describeConsumerGroups(java.util.List.of(consumerGroupId))
                    .all()
                    .get(10, TimeUnit.SECONDS);
            ConsumerGroupDescription desc = descriptions.get(consumerGroupId);
            return desc != null && desc.members() != null && !desc.members().isEmpty();
        } catch (Exception e) {
            log.warn("Could not describe consumer group {}: {}", consumerGroupId, e.getMessage());
            return false;
        }
    }

    public long getCommittedOffsetForTopic(String consumerGroupId, String topic) {
        try (AdminClient admin = createAdminClient()) {
            Map<TopicPartition, OffsetAndMetadata> offsets = admin
                    .listConsumerGroupOffsets(consumerGroupId)
                    .partitionsToOffsetAndMetadata()
                    .get(10, TimeUnit.SECONDS);
            return offsets.entrySet().stream()
                    .filter(e -> topic.equals(e.getKey().topic()))
                    .mapToLong(e -> e.getValue().offset())
                    .sum();
        } catch (Exception e) {
            log.warn("Could not read committed offsets for group {} topic {}: {}",
                    consumerGroupId, topic, e.getMessage());
            return 0L;
        }
    }

    private AdminClient createAdminClient() {
        return AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }
}
