package com.example.swipes_demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.KafkaSender;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaSender<String, String> reactiveKafkaProducerTemplate(
            KafkaProperties properties) {
        return KafkaSender.create(
                SenderOptions.create(properties.buildProducerProperties())
        );
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}