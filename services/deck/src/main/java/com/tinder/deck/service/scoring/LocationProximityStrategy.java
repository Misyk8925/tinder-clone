package com.tinder.deck.service.scoring;


import com.tinder.deck.dto.SharedProfileDto;
import org.springframework.stereotype.Component;

@Component
public class LocationProximityStrategy implements ScoringStrategy {

    @Override
    public double calculateScore(SharedProfileDto viewer, SharedProfileDto candidate) {
        if (viewer.location() == null || candidate.location() == null) {
            return 0.0;
        }

        if (viewer.location().latitude() == null || viewer.location().longitude() == null ||
            candidate.location().latitude() == null || candidate.location().longitude() == null) {
            return 0.0;
        }

        double distance = calculateDistance(
                viewer.location().latitude(),
                viewer.location().longitude(),
                candidate.location().latitude(),
                candidate.location().longitude()
        );

        Integer maxRange = viewer.preferences() != null
                ? viewer.preferences().maxRange()
                : 100;

        if (distance <= maxRange) {
            return (1.0 - distance / maxRange) * getWeight();
        }

        return 0.0;
    }

    @Override
    public double getWeight() {
        return 0.8;
    }

    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula implementation

        double R = 6371; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}