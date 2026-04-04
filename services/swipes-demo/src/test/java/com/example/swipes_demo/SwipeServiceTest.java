package com.example.swipes_demo;

import com.example.swipes_demo.profileCache.ProfileCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SwipeServiceTest {

    private static final String BEARER_TOKEN = "test-token";

    @Mock
    private SwipeProducer swipeProducer;

    @Mock
    private ProfileCacheService profileCacheService;

    @InjectMocks
    private SwipeService swipeService;

    private Jwt jwt() {
        return Jwt.withTokenValue(BEARER_TOKEN)
                .header("alg", "none")
                .claim("sub", "user-id")
                .build();
    }

    @Test
    void sendSwipeShouldRejectWhenProfilesDoNotExist() {
        String profile1Id = UUID.randomUUID().toString();
        String profile2Id = UUID.randomUUID().toString();
        SwipeDto dto = new SwipeDto(profile1Id, profile2Id, true, null);

        when(profileCacheService.existsAll(UUID.fromString(profile1Id), UUID.fromString(profile2Id), BEARER_TOKEN))
                .thenReturn(Mono.just(false));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> swipeService.sendSwipe(dto, false, jwt()).block()
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getReason()).isEqualTo("One or both profiles were not found");
        verify(swipeProducer, never()).send(any());
    }

    @Test
    void sendSwipeShouldPublishEventWhenProfilesExist() {
        String profile1Id = UUID.randomUUID().toString();
        String profile2Id = UUID.randomUUID().toString();
        SwipeDto dto = new SwipeDto(profile1Id, profile2Id, false, null);

        when(profileCacheService.existsAll(UUID.fromString(profile1Id), UUID.fromString(profile2Id), BEARER_TOKEN))
                .thenReturn(Mono.just(true));
        when(swipeProducer.send(any())).thenReturn(Mono.empty());

        swipeService.sendSwipe(dto, false, jwt()).block();

        ArgumentCaptor<SwipeCreatedEvent> eventCaptor = ArgumentCaptor.forClass(SwipeCreatedEvent.class);
        verify(swipeProducer).send(eventCaptor.capture());
        SwipeCreatedEvent sentEvent = eventCaptor.getValue();

        assertThat(sentEvent.getProfile1Id()).isEqualTo(profile1Id);
        assertThat(sentEvent.getProfile2Id()).isEqualTo(profile2Id);
        assertThat(sentEvent.isDecision()).isFalse();
        assertThat(sentEvent.getEventId()).isNotBlank();
        assertThat(sentEvent.getTimestamp()).isPositive();
    }

    @Test
    void sendSwipeShouldRejectWhenProfileIdsAreEqual() {
        String sameId = UUID.randomUUID().toString();
        SwipeDto dto = new SwipeDto(sameId, sameId, true, null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> swipeService.sendSwipe(dto, false, jwt()).block()
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("profile1Id and profile2Id must be different");
        verify(profileCacheService, never()).existsAll(any(), any(), any());
        verify(swipeProducer, never()).send(any());
    }
}
