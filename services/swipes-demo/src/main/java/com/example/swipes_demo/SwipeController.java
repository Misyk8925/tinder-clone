package com.example.swipes_demo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/swipes")
@RequiredArgsConstructor
public class SwipeController {

    private final SwipeProducer swipeProducer;

    @PostMapping
    public Mono<ResponseEntity<Void>> swipe(@RequestBody @Valid SwipeDto dto) {
        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(dto.profile1Id())
                .profile2Id(dto.profile2Id())
                .decision(dto.decision())
                .timestamp(System.currentTimeMillis())
                .build();

        return swipeProducer.send(event)
                .thenReturn(ResponseEntity.<Void>ok().build());
    }
}
