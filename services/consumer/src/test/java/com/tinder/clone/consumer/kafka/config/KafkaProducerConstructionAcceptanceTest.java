package com.tinder.clone.consumer.kafka.config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class KafkaProducerConstructionAcceptanceTest {

    @Test
    void givenConfiguredOutboxProducerWhenConstructedThenProducerStarts() {
        Map<String, Object> props = producerPropsFromApplicationYml();

        assertThatCode(() -> {
            try (KafkaProducer<Object, Object> producer = new KafkaProducer<>(props)) {
                assertThat(producer).isNotNull();
            }
        }).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> producerPropsFromApplicationYml() {
        Yaml yaml = new Yaml();
        try (InputStream in = KafkaProducerConstructionAcceptanceTest.class.getResourceAsStream("/application.yml")) {
            assertThat(in).as("application.yml must be on the test classpath").isNotNull();
            Map<String, Object> root = yaml.load(in);
            Map<String, Object> producer = nested(root, "spring", "kafka", "producer");

            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, producer.get("key-serializer"));
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, producer.get("value-serializer"));
            props.put(ProducerConfig.ACKS_CONFIG, producer.get("acks"));
            props.put(ProducerConfig.RETRIES_CONFIG, producer.get("retries"));

            Object extra = producer.get("properties");
            if (extra instanceof Map<?, ?> extraProps) {
                extraProps.forEach((key, value) -> props.put(String.valueOf(key), value));
            }
            return props;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load producer properties from application.yml", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> root, String... keys) {
        Map<String, Object> current = root;
        for (String key : keys) {
            Object next = current.get(key);
            assertThat(next)
                    .as("application.yml is missing %s under %s", key, String.join(".", keys))
                    .isInstanceOf(Map.class);
            current = (Map<String, Object>) next;
        }
        return current;
    }
}
