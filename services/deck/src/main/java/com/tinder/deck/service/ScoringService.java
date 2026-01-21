package com.tinder.deck.service;

import com.tinder.deck.dto.SharedProfileDto;
import com.tinder.deck.service.scoring.ScoringStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScoringService {

    private final List<ScoringStrategy> strategies;

    public double score(SharedProfileDto viewer, SharedProfileDto candidate) {
        return strategies.stream()
                .mapToDouble(strategy -> strategy.calculateScore(viewer, candidate))
                .sum();
    }
}
