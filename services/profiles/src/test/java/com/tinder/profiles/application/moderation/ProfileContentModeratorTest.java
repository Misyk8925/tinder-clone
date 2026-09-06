package com.tinder.profiles.application.moderation;

import com.tinder.profiles.application.photos.exception.PhotoContentBlockedException;
import com.tinder.profiles.application.profile.exception.ContentBlockedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProfileContentModerator")
class ProfileContentModeratorTest {

    @Test
    @DisplayName("Given a blocked bio, when the profile is saved, then the write is rejected")
    void blockedTextIsRejected() {
        ProfileContentModerator moderator = new ProfileContentModerator(fixed("BLOCK"));

        assertThatThrownBy(() -> moderator.requireAllowedText("profile:1", "spam", "user-1"))
                .isInstanceOf(ContentBlockedException.class)
                .hasMessageContaining("blocked by moderation");
    }

    @Test
    @DisplayName("Given an allowed bio, when the profile is saved, then the write continues")
    void allowedTextPasses() {
        new ProfileContentModerator(fixed("ALLOW")).requireAllowedText("profile:1", "hello", "user-1");
    }

    @Test
    @DisplayName("Given a blocked photo URL, when it is uploaded, then the image is rejected")
    void blockedPhotoIsRejected() {
        ProfileContentModerator moderator = new ProfileContentModerator(fixed("BLOCK"));

        assertThatThrownBy(() -> moderator.requireAllowedPhoto("photo:1", "https://cdn.example/a.jpg", "user-1"))
                .isInstanceOf(PhotoContentBlockedException.class);
    }

    @Test
    @DisplayName("Given blank text, when moderated, then the classifier is not required")
    void blankTextIsSkipped() {
        AtomicInteger calls = new AtomicInteger();
        ProfileContentModerator moderator = new ProfileContentModerator(new StubPort(calls, "ALLOW"));

        moderator.requireAllowedText("profile:1", "  ", "user-1");
        assertThat(calls.get()).isZero();
    }

    private static ModerationPort fixed(String decision) {
        return new StubPort(new AtomicInteger(), decision);
    }

    private static final class StubPort implements ModerationPort {
        private final AtomicInteger calls;
        private final String decision;

        private StubPort(AtomicInteger calls, String decision) {
            this.calls = calls;
            this.decision = decision;
        }

        @Override
        public ModerationDecision moderateText(String contentId, ModerationContentType type, String text, String authorId) {
            calls.incrementAndGet();
            return new ModerationDecision(decision, "CATEGORY_BLOCK");
        }

        @Override
        public ModerationDecision moderateImages(String contentId, ModerationContentType type, List<String> imageUrls, String authorId) {
            calls.incrementAndGet();
            return new ModerationDecision(decision, "CATEGORY_BLOCK");
        }
    }
}
