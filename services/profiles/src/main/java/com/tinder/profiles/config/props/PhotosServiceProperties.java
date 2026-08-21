package com.tinder.profiles.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Photos service client settings ({@code photos.*}). */
@ConfigurationProperties(prefix = "photos")
public record PhotosServiceProperties(@DefaultValue Service service) {

    public record Service(@DefaultValue("http://localhost:8070") String url) {
    }
}
