package com.tinder.profiles.application.profile.support;

import com.tinder.profiles.application.profile.command.ProfileEditCommand;
import com.tinder.profiles.application.profile.exception.ProfileValidationException;
import com.tinder.profiles.application.profile.model.PreferencesData;
import com.tinder.profiles.application.profile.model.ProfileEdit;
import com.tinder.profiles.application.profile.port.out.TextSanitizerPort;
import com.tinder.profiles.domain.profile.DomainValidationException;
import com.tinder.profiles.domain.profile.GeoPoint;
import com.tinder.profiles.domain.profile.Hobby;
import com.tinder.profiles.domain.profile.MatchingPreferences;
import com.tinder.profiles.domain.profile.Profile;
import com.tinder.profiles.domain.profile.ProfileChangeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Applies edit semantics around the domain aggregate: translates a command into
 * domain value objects and works out what actually changed. Patch-specific null
 * handling belongs in application rather than in the domain model.
 *
 * <p>This is also the single place where inbound text is sanitized — every
 * user-supplied string on the command, including the gender inside
 * {@link PreferencesData} — and where domain invariant violations are translated
 * into the application's {@link ProfileValidationException}, so no domain
 * exception ever reaches an inbound adapter.
 */
@Component
@RequiredArgsConstructor
public class ProfileEditService {

    private final TextSanitizerPort sanitizer;

    /** Sanitizes the command's text and translates it into domain value objects. */
    public ProfileEdit toEdit(ProfileEditCommand cmd) {
        return new ProfileEdit(
                sanitizer.sanitizePlainText(cmd.name()),
                cmd.age(),
                sanitizer.sanitizePlainText(cmd.gender()),
                sanitizer.sanitizePlainText(cmd.bio()),
                sanitizer.sanitizePlainText(cmd.city()),
                GeoPoint.of(cmd.latitude(), cmd.longitude()).orElse(null),
                toMatchingPreferences(cmd.preferences()),
                toHobbies(cmd.hobbies()));
    }

    public void requireLocationProvided(ProfileEdit edit) {
        if (!edit.hasCity() && !edit.hasPosition()) {
            throw new ProfileValidationException("Either city or GPS coordinates must be provided");
        }
    }

    public ProfileChangeSet detectChanges(Profile existing, ProfileEdit incoming) {
        ProfileChangeSet changes = ProfileChangeSet.empty();

        if (incoming.name() != null && !Objects.equals(existing.getName(), incoming.name())) {
            changes.add("name");
        }
        if (incoming.age() != null && !Objects.equals(existing.getAge(), incoming.age())) {
            changes.add("age");
        }
        if (incoming.gender() != null && !Objects.equals(existing.getGender(), incoming.gender())) {
            changes.add("gender");
        }
        if (incoming.bio() != null && !Objects.equals(existing.getBio(), incoming.bio())) {
            changes.add("bio");
        }
        if (incoming.city() != null && !Objects.equals(existing.getCity(), incoming.city())) {
            changes.add("city");
        }
        if (incoming.preferences() != null) {
            MatchingPreferences current = existing.getPreferences();
            if (current == null || !current.sameCriteriaAs(incoming.preferences())) {
                changes.markPreferencesChanged();
            }
        }
        if (incoming.hobbies() != null) {
            changes.add("hobbies");
        }

        return changes;
    }

    private MatchingPreferences toMatchingPreferences(PreferencesData preferences) {
        if (preferences == null) {
            return null;
        }
        try {
            return new MatchingPreferences(
                    preferences.minAge(),
                    preferences.maxAge(),
                    // Free text like the profile's own gender, and equally user-supplied.
                    sanitizer.sanitizePlainText(preferences.gender()),
                    preferences.maxRange());
        } catch (DomainValidationException e) {
            throw new ProfileValidationException(e.getMessage());
        }
    }

    private List<Hobby> toHobbies(List<String> names) {
        if (names == null) {
            return null;
        }
        return names.stream().map(this::toHobby).toList();
    }

    private Hobby toHobby(String name) {
        try {
            return Hobby.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ProfileValidationException("hobbies", "unknown hobby '" + name + "'");
        }
    }
}
