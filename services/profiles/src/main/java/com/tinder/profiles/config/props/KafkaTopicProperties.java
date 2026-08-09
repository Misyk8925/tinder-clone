package com.tinder.profiles.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Destination topics for profile domain events ({@code kafka.topics.profile-events.*}). */
@ConfigurationProperties(prefix = "kafka.topics.profile-events")
public record KafkaTopicProperties(

        @DefaultValue("profile.created") String created,

        @DefaultValue("profile.updated") String updated,

        @DefaultValue("profile.deleted") String deleted
) {
}
