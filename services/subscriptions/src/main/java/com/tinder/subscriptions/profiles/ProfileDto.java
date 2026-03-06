package com.tinder.subscriptions.profiles;

import java.util.UUID;

public record ProfileDto(
        UUID profileId,
        String userId
) {
}
