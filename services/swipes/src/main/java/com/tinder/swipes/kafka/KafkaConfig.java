package com.tinder.swipes.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topic.swipe-created}")
    private String swipeCreatedTopic;

    @Bean
    public NewTopic swipeCreatedTopic(){
        return TopicBuilder.name(swipeCreatedTopic)
                .partitions(10)
                .replicas(1)
                .config("retention.ms", "604800000") // 7 days
                .config("cleanup.policy", "delete")
                .build();
    }
}
