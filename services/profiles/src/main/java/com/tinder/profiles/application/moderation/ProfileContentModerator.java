package com.tinder.profiles.application.moderation;

import com.tinder.profiles.application.photos.exception.PhotoContentBlockedException;
import com.tinder.profiles.application.profile.exception.ContentBlockedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProfileContentModerator {
    private static final String BLOCKED_TEXT =
            "This content was blocked by moderation. Please choose different wording.";
    private static final String BLOCKED_PHOTO =
            "This photo was blocked by moderation. Please choose another image.";

    private final ModerationPort moderation;

    public void requireAllowedText(String contentId, String text, String authorId) {
        if (text == null || text.isBlank()) {
            return;
        }
        ModerationDecision decision = moderation.moderateText(
                contentId, ModerationContentType.PROFILE_DESCRIPTION, text, authorId);
        if (decision.blocked()) {
            throw new ContentBlockedException(BLOCKED_TEXT);
        }
    }

    public void requireAllowedPhoto(String contentId, String imageUrl, String authorId) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        ModerationDecision decision = moderation.moderateImages(
                contentId, ModerationContentType.PHOTO, List.of(imageUrl), authorId);
        if (decision.blocked()) {
            throw new PhotoContentBlockedException(BLOCKED_PHOTO);
        }
    }

    public void submitReport(String contentId, String text, String authorId) {
        moderation.moderateText(contentId, ModerationContentType.REPORT, text, authorId);
    }
}
