package com.tinder.deck.service.scoring;

import com.tinder.deck.dto.SharedProfileDto;
import org.springframework.stereotype.Component;

@Component
public class AgeCompatibilityStrategy implements ScoringStrategy{

    @Override
    public double calculateScore(SharedProfileDto viewer, SharedProfileDto candidate) {
        if (viewer.preferences() == null) return 0.5;

        Integer minAge = viewer.preferences().minAge();
        Integer maxAge = viewer.preferences().maxAge();

        if (minAge == null || maxAge == null) return 0.5;

        int age = candidate.age();
        if (age >= minAge && age <= maxAge) {
            // Perfect match
            return getWeight();
        } else if (age < minAge) {
            // Younger than preferred
            double diff = minAge - age;
            return Math.max(0, (1.0 - diff / 10.0)) * getWeight();
        } else {
            // Older than preferred
            double diff = age - maxAge;
            return Math.max(0, (1.0 - diff / 10.0)) * getWeight();
        }
    }

    @Override
    public double getWeight() {
        return 1.0;
    }
}
