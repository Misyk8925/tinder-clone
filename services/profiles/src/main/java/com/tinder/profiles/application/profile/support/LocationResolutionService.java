package com.tinder.profiles.application.profile.support;

import com.tinder.profiles.application.profile.port.out.LocationPort;
import com.tinder.profiles.application.profile.port.out.ResolvedLocation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Thin helper that picks the right {@link LocationPort} call: coordinate-based
 * when GPS data is present, city-name-based otherwise. The decision of <em>whether</em>
 * to resolve (jitter threshold, city-changed) stays in the use cases.
 */
@Component
@RequiredArgsConstructor
public class LocationResolutionService {

    private final LocationPort locationPort;

    public ResolvedLocation resolve(Double latitude, Double longitude, String city) {
        if (latitude != null && longitude != null) {
            return locationPort.resolveFromCoordinates(latitude, longitude, city);
        }
        return locationPort.resolve(city);
    }
}
