package com.tinder.deck.service.scoring;

import com.tinder.deck.dto.SharedProfileDto;

public interface ScoringStrategy {

    double calculateScore(SharedProfileDto viewer, SharedProfileDto candidate);

    double getWeight();
}
