package com.tinder.clone.consumer.kafka;

import com.tinder.clone.consumer.kafka.event.MatchCreateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderRecord;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchEventProducer {

    private final KafkaTemplate<String, MatchCreateEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.match-created}")
    private String topic;

    public Mono<Void> send(MatchCreateEvent event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .flatMap(json -> Mono.fromFuture(
                        kafkaTemplate.send(topic, event.getEventId(), event)
                ))
                .doOnError(e -> log.error("Failed to send swipe event", e))
                .then();
    }


}
