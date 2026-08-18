package com.tinder.profiles.application.profile.port.in;

import com.tinder.profiles.application.profile.query.ProfileView;

import java.util.UUID;

/**
 * Read-side use-case boundary. An interface is justified here because the
 * persistence adapter implements it across the application/adapter boundary.
 */
public interface ProfileQuery {

    ProfileView getOne(UUID id);

    ProfileView getMyProfile(String userId);

    UUID getActiveProfileIdByUserId(String userId);
}
