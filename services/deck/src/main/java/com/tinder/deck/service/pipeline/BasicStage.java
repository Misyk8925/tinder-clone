package com.tinder.deck.service.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BasicStage {

    @Value("200")
      int batchSize;

    @Value("${deck.request-timeout-ms}")
     long timeoutMs;

    @Value("${deck.retries:1}")
     long retries;

    @Value("${deck.parallelism:8}")
     int parallelism;
}
