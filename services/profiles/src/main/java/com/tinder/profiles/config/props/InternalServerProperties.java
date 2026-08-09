package com.tinder.profiles.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The second HTTPS connector that serves {@code /internal/**} with mTLS
 * ({@code internal.server.*}). Only the deck service is expected to call it.
 */
@ConfigurationProperties(prefix = "internal.server")
public record InternalServerProperties(

        @DefaultValue("8011") int port,

        @DefaultValue Ssl ssl
) {

    public record Ssl(

            @DefaultValue("true") boolean enabled,

            @DefaultValue("classpath:profiles-service.p12") String keyStore,

            @DefaultValue("changeit") String keyStorePassword,

            @DefaultValue("PKCS12") String keyStoreType,

            @DefaultValue("classpath:truststore.jks") String trustStore,

            @DefaultValue("changeit") String trustStorePassword,

            @DefaultValue("JKS") String trustStoreType,

            /** {@code need} requires a client certificate, {@code want} makes it optional. */
            @DefaultValue("need") String clientAuth
    ) {
    }
}
