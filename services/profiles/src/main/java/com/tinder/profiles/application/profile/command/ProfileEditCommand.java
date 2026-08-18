package com.tinder.profiles.application.profile.command;

import com.tinder.profiles.application.profile.model.PreferencesData;

import java.util.List;

/**
 * The fields every profile-writing command carries. Create, update and patch
 * differ only in how missing values are interpreted, so the shared translation
 * into a {@code ProfileEdit} lives in one place
 * ({@code ProfileEditService#toEdit}).
 */
public sealed interface ProfileEditCommand
        permits CreateProfileCommand, UpdateProfileCommand, PatchProfileCommand {

    String userId();

    String name();

    Integer age();

    String gender();

    String bio();

    String city();

    PreferencesData preferences();

    /** Hobby names from the {@code Hobby} vocabulary; unknown names are rejected. */
    List<String> hobbies();

    Double latitude();

    Double longitude();
}
