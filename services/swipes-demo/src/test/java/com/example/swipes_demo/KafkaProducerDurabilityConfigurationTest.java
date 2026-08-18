package com.example.swipes_demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.PropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerDurabilityConfigurationTest {

    @Test
    void correctnessCriticalSwipeProducerRequiresAllReplicasAndIdempotence() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));

        assertThat(property(sources, "spring.kafka.producer.acks")).isEqualTo("all");
        assertThat(property(sources, "spring.kafka.producer.properties.enable.idempotence")).isEqualTo(true);
        assertThat(property(sources, "spring.kafka.producer.properties.max.in.flight.requests.per.connection"))
                .isEqualTo(5);
    }

    private Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }
}
