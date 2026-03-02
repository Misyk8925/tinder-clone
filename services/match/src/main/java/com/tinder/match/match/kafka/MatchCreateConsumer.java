package com.tinder.match.match.kafka;

import com.tinder.match.match.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchCreateConsumer {

    private final MatchService matchService;

    @KafkaListener(
            topics = "${app.kafka.topic.match-created}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleMatchCreateEvent(
            @Payload MatchCreateEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
            ){
        log.info("Received MatchCreate Event in partition: {}, offset: {}",
                partition, offset);
        try {
            matchService.create(event);
            acknowledgment.acknowledge();
            log.info("Processed and acknowledged MatchCreateEvent: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process MatchCreateEvent: {}", event, e);
            throw e;
        }
    }

}
