package com.tinder.clone.consumer;

import com.tinder.clone.consumer.model.dto.SwipeRecordDto;
import com.tinder.clone.consumer.service.SwipeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SwipeController {

    private final SwipeService service;

    /**
     * Batch check if swipes exist between viewer and list of candidates
     * Used by deck service to filter out already swiped profiles
     */
    @PostMapping(value = "/between/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<UUID, Boolean> betweenBatch(@RequestParam("viewerId") UUID viewerId,
                                           @RequestBody List<UUID> candidateIds) {
        log.debug("Internal batch swipe check: viewerId={}, candidateCount={}", viewerId, candidateIds.size());
        return service.existsBetweenBatch(viewerId, candidateIds);
    }


}
