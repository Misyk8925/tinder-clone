package com.tinder.profiles.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Location service client settings and movement policy ({@code location.*}). */
@ConfigurationProperties(prefix = "location")
public record LocationProperties(

        @DefaultValue Service service,

        @DefaultValue Change change
) {

    public record Service(@DefaultValue("http://localhost:8065") String url) {
    }

    /** How far a profile must move before the coordinates are re-resolved. */
    public record Change(@DefaultValue("1.0") double thresholdKm) {
    }
}
