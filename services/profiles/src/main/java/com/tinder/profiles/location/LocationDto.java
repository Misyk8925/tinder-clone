package com.tinder.profiles.location;

import jakarta.validation.constraints.NotNull;
import lombok.Value;
import org.locationtech.jts.geom.Point;

/**
 * DTO for {@link Location}
 */
@Value
public class LocationDto {
    @NotNull
    Point geo;
}