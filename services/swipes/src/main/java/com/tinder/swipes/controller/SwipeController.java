package com.tinder.swipes.controller;

import com.tinder.swipes.model.dto.SwipeRecordDto;
import com.tinder.swipes.service.SwipeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SwipeController {

    private final SwipeService service;

    @PostMapping()
    public ResponseEntity<?> swipe(@RequestBody SwipeRecordDto swipeRecord) {
        log.info("Received swipe record: {}", swipeRecord);
        service.save(swipeRecord);
        return ResponseEntity
                .status(200)
                .body(swipeRecord);
    }
}
