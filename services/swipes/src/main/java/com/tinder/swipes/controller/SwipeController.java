package com.tinder.swipes.controller;

import com.tinder.swipes.model.dto.SwipeRecordDto;
import com.tinder.swipes.service.SwipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/swipe")
@RequiredArgsConstructor
public class SwipeController {

    private final SwipeService service;

    @PostMapping()
    public ResponseEntity<?> swipe(@RequestBody SwipeRecordDto swipeRecord) {
        service.save(swipeRecord);
        return ResponseEntity
                .status(200)
                .body(swipeRecord);
    }
}
