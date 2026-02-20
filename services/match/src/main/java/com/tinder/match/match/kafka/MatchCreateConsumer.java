package com.tinder.match.match.kafka;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.kafka.topic.match-created}")
    private String topic;

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


    }

}
