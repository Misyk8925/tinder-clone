package com.tinder.match.match.kafka;

import com.tinder.match.match.MatchService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MatchCreateConsumerTest {

    @Test
    void databaseFailureReachesKafkaErrorHandlerWithoutAcknowledgingOffset() {
        MatchService matchService = mock(MatchService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        MatchCreateEvent event = MatchCreateEvent.builder().eventId("match-1").build();
        doThrow(new IllegalStateException("database unavailable")).when(matchService).create(event);

        MatchCreateConsumer consumer = new MatchCreateConsumer(matchService);

        assertThatThrownBy(() -> consumer.handleMatchCreateEvent(event, 0, 1L, acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verifyNoInteractions(acknowledgment);
    }
}
