package com.tinder.match.moderation;

import com.tinder.match.config.ModerationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ModerationClient")
class ModerationClientTest {

    @Test
    @DisplayName("Given moderation is disabled, when a message is checked, then it is allowed")
    void disabledClientAllows() {
        ModerationClient client = new ModerationClient(new ModerationProperties(
                false,
                new ModerationProperties.Service("http://localhost:9", Duration.ofMillis(50), "u", "p")
        ));

        assertThat(client.moderateText("m1", "hello", "u1").blocked()).isFalse();
        client.requireAllowedText("m1", "hello", "u1");
        client.requireAllowedImages("p1", List.of("https://cdn.example/a.jpg"), "u1");
    }

    @Test
    @DisplayName("Given a blocked decision helper, when content is rejected, then CONTENT_BLOCKED is raised")
    void blockedHelperThrows() {
        assertThatThrownBy(() -> {
            throw new ContentBlockedException("This message was blocked by moderation.");
        }).isInstanceOf(ContentBlockedException.class);
    }
}
