package com.tinder.deck.service.scoring;


import com.tinder.deck.dto.SharedProfileDto;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

@Component
public class LocationProximityStrategy implements ScoringStrategy {

    @Override
    public double calculateScore(SharedProfileDto viewer, SharedProfileDto candidate) {
        if (viewer.location() == null || candidate.location() == null) {
            return 0.0;
        }

        double distance = calculateDistance(
                viewer.location().geo(),
                candidate.location().geo()
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

    private double calculateDistance(Point p1, Point p2) {
        // Haversine formula implementation
        double lat1 = p1.getY();
        double lon1 = p1.getX();
        double lat2 = p2.getY();
        double lon2 = p2.getX();

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