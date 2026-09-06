package com.tinder.profiles.application.moderation;

import java.util.List;

public interface ModerationPort {
    ModerationDecision moderateText(String contentId, ModerationContentType type, String text, String authorId);

    ModerationDecision moderateImages(String contentId, ModerationContentType type, List<String> imageUrls, String authorId);
}
