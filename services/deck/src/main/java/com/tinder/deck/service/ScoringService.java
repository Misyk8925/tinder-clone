package com.tinder.deck.service;

import com.tinder.deck.dto.SharedProfileDto;
import org.springframework.stereotype.Service;

@Service
public class ScoringService {

    public double score(SharedProfileDto viewerProfile, SharedProfileDto targetProfile) {
        // double score = 0;
        double ageFit = viewerProfile.preferences().minAge() != null && viewerProfile.preferences().maxAge() != null
                ? targetProfile.age() <= viewerProfile.preferences().maxAge() && targetProfile.age() >= viewerProfile.preferences().minAge() ? 1 : 0
                :0.5;
        return ageFit;
    }
}
