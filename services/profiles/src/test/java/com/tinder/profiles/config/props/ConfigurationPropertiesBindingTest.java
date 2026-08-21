package com.tinder.profiles.config.props;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Binds every properties record against the real {@code application.yml}. Records
 * fail fast on missing values, so this is what catches a renamed key or a broken
 * default without booting the whole application.
 */
@DisplayName("Configuration properties")
class ConfigurationPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesUnderTest.class);

    @Test
    @DisplayName("bind from application.yml")
    void bindFromApplicationYml() {
        runner.run(context -> {
            then(context).hasNotFailed();

            then(context.getBean(AwsProperties.class).region()).isNotBlank();

            InternalServerProperties internal = context.getBean(InternalServerProperties.class);
            then(internal.port()).isEqualTo(8011);
            then(internal.ssl().enabled()).isTrue();
            then(internal.ssl().clientAuth()).isEqualTo("need");
            then(internal.ssl().keyStoreType()).isEqualTo("PKCS12");

            KeycloakProperties keycloak = context.getBean(KeycloakProperties.class);
            then(keycloak.realm()).isEqualTo("spring");
            then(keycloak.keycloakUrl()).startsWith("http");

            KafkaTopicProperties topics = context.getBean(KafkaTopicProperties.class);
            then(topics.created()).isEqualTo("profile.created");
            then(topics.updated()).isEqualTo("profile.updated");
            then(topics.deleted()).isEqualTo("profile.deleted");
            then(topics.deckCardProjection()).isEqualTo("profile.deck-card-projection.v1");

            LocationProperties location = context.getBean(LocationProperties.class);
            then(location.service().url()).startsWith("http");
            then(location.change().thresholdKm()).isEqualTo(1.0);

            PhotosServiceProperties photosService = context.getBean(PhotosServiceProperties.class);
            then(photosService.service().url()).isEqualTo("http://localhost:8070");

            OutboxPublisherProperties outbox = context.getBean(OutboxPublisherProperties.class);
            then(outbox.enabled()).isTrue();
            then(outbox.batchSize()).isEqualTo(100);
            then(outbox.maxRetries()).isEqualTo(10);
            then(outbox.maxErrorLength()).isEqualTo(1000);   // default, absent from the yml

            ProfileCacheProperties caches = context.getBean(ProfileCacheProperties.class);
            then(caches.jwtToken().ttl()).isEqualTo(Duration.ofMinutes(5));
            then(caches.jwtProfileId().ttl()).isEqualTo(Duration.ofMinutes(30));
            then(caches.sharedProfile().maxSize()).isEqualTo(250_000);

            PhotoProperties photos = context.getBean(PhotoProperties.class);
            then(photos.s3().presignExpSeconds()).isEqualTo(300);
            then(photos.photos().maxPerProfile()).isEqualTo(5);
            then(photos.photos().maxSize().toBytes()).isEqualTo(5L * 1024 * 1024);
            then(photos.photos().allowedContentTypes())
                    .containsExactly("image/jpeg", "image/png", "image/webp");
            then(photos.photos().minDimensionPx()).isEqualTo(300);
            then(photos.photos().maxDimensionPx()).isEqualTo(4096);

            then(context.getBean(ProfileCleanupProperties.class).retentionDays()).isEqualTo(30);
        });
    }

    @Configuration
    @EnableConfigurationProperties({
            AwsProperties.class,
            InternalServerProperties.class,
            KafkaTopicProperties.class,
            KeycloakProperties.class,
            LocationProperties.class,
            OutboxPublisherProperties.class,
            PhotoProperties.class,
            PhotosServiceProperties.class,
            ProfileCacheProperties.class,
            ProfileCleanupProperties.class
    })
    static class PropertiesUnderTest {
    }
}
