package com.tinder.swipes.controller;

import com.tinder.swipes.model.dto.SwipeRecordDto;
import com.tinder.swipes.service.SwipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/swipes")
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

    @PostMapping(value = "/between/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<UUID, Boolean> betweenBatch(@RequestParam("viewerId") UUID viewerId,
                                           @RequestBody List<UUID> candidateIds) {
        return service.existsBetweenBatch(viewerId, candidateIds);
    }
}
