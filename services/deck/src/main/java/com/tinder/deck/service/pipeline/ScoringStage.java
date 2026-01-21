package com.tinder.deck.service.pipeline;

import com.tinder.deck.dto.SharedProfileDto;
import com.tinder.deck.service.ScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Comparator;
import java.util.UUID;

@RequiredArgsConstructor
@Component
@Slf4j
public class ScoringStage extends BasicStage{

    private final ScoringService scoringService;

    public Flux<ScoredCandidate> scoreAndRank(
            SharedProfileDto viewer,
            Flux<SharedProfileDto> candidates) {

        log.debug("Scoring and ranking candidates for viewer {}", viewer.id());

        return candidates
                .parallel(parallelism)
                .runOn(Schedulers.parallel())
                .map(candidate -> scoreCandidate(viewer, candidate))
                .sequential()
                .sort(Comparator.comparingDouble(ScoredCandidate::score).reversed())
                .doOnComplete(() -> log.debug("Scoring completed for viewer {}", viewer.id()));
    }

    private ScoredCandidate scoreCandidate(SharedProfileDto viewer, SharedProfileDto candidate) {
        double score = scoringService.score(viewer, candidate);
        return new ScoredCandidate(candidate.id(), score);
    }

    public record ScoredCandidate(UUID candidateId, double score) {}
}
