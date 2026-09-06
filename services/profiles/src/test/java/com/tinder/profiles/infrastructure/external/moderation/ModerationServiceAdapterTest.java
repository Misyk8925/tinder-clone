package com.tinder.profiles.infrastructure.external.moderation;

import com.tinder.profiles.application.moderation.ModerationContentType;
import com.tinder.profiles.application.moderation.ModerationDecision;
import com.tinder.profiles.config.props.ModerationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ModerationServiceAdapter")
class ModerationServiceAdapterTest {

    @Test
    @DisplayName("Given moderation is disabled, when content is checked, then it is allowed without a network call")
    void disabledClientAllows() {
        ModerationProperties properties = new ModerationProperties(
                false,
                new ModerationProperties.Service("http://localhost:9", Duration.ofMillis(50), "u", "p")
        );
        ModerationServiceAdapter adapter = new ModerationServiceAdapter(
                WebClient.builder().baseUrl("http://localhost:9").build(),
                properties
        );

        ModerationDecision text = adapter.moderateText("c1", ModerationContentType.PROFILE_DESCRIPTION, "hello", "u1");
        ModerationDecision images = adapter.moderateImages("c2", ModerationContentType.PHOTO, List.of("https://cdn.example/a.jpg"), "u1");

        assertThat(text.blocked()).isFalse();
        assertThat(images.blocked()).isFalse();
    }
}
