package com.example.swipes_demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Service
@RequiredArgsConstructor
@Slf4j
public class SwipeProducer {

    private final KafkaSender<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic = "swipe-created";

    public Mono<Void> send(SwipeCreatedEvent event) {

            return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                    .map(json -> SenderRecord.create(
                            new ProducerRecord<>(topic, event.getProfile1Id(), json), null))
                    .as(kafkaTemplate::send)
                    .next()
                    .doOnError(e -> log.error("Failed to send swipe event", e))
                    .then();

    }
}
