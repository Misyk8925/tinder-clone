package com.tinder.profiles.application.profile.command;

import java.time.LocalDateTime;

/** Application-layer intent to change a profile's premium status. */
public record UpdatePremiumStatusCommand(String userId, boolean premium, LocalDateTime expiresAt) {
}
