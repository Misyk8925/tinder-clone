package com.tinder.profiles.application.profile.command;

import com.tinder.profiles.application.profile.model.PreferencesData;

import java.util.List;

/**
 * Application-layer intent to create a profile. Built by the API mapper from the
 * inbound DTO; carries application types, not transport or domain ones. The text
 * it carries is still raw — sanitizing happens in
 * {@code ProfileEditService#toEdit}, downstream of every write command.
 */
public record CreateProfileCommand(
        String userId,
        String name,
        Integer age,
        String gender,
        String bio,
        String city,
        PreferencesData preferences,
        List<String> hobbies,
        Double latitude,
        Double longitude
) implements ProfileEditCommand {
}
