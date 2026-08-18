package com.example.swipes_demo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwipeProducerTest {

    private SwipeProducer producer;

    @AfterEach
    void stopProducer() {
        if (producer != null) {
            producer.stopSender();
        }
    }

    @Test
    void givenBrokerHasNotAcknowledged_whenSwipeIsEnqueued_thenRequestDoesNotComplete() {
        KafkaSender<String, String> sender = mock(KafkaSender.class);
        when(sender.send(any())).thenReturn(Flux.never());
        producer = new SwipeProducer(sender, 10, 1, 10, Duration.ofMillis(1), false);
        producer.startSender();

        assertThatThrownBy(() -> producer.send(event()).block(Duration.ofMillis(100)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timeout");
    }

    @Test
    void givenBrokerSendFails_whenSwipeIsEnqueued_thenRequestFails() {
        KafkaSender<String, String> sender = mock(KafkaSender.class);
        when(sender.send(any())).thenReturn(Flux.error(new IllegalStateException("broker unavailable")));
        producer = new SwipeProducer(sender, 10, 1, 10, Duration.ofMillis(1), false);
        producer.startSender();

        assertThatThrownBy(() -> producer.send(event()).block(Duration.ofSeconds(2)))
                .isInstanceOf(ResponseStatusException.class)
                .hasRootCauseMessage("broker unavailable")
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(503));
    }

    private SwipeCreatedEvent event() {
        return new SwipeCreatedEvent(
                "event-1",
                "249bea58-449e-4bb6-9243-8f16efec14e0",
                "44799e38-8299-4697-a8a1-2c56ccededfd",
                true,
                false,
                System.currentTimeMillis()
        );
    }
}
