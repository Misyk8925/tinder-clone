package com.tinder.profiles.api.profile.mapper;

import com.tinder.profiles.api.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.api.profile.dto.profileData.PatchProfileDto;
import com.tinder.profiles.application.profile.command.CreateProfileCommand;
import com.tinder.profiles.application.profile.command.PatchProfileCommand;
import com.tinder.profiles.application.profile.command.UpdateProfileCommand;
import com.tinder.profiles.application.profile.port.out.TextSanitizerPort;
import com.tinder.profiles.domain.profile.MatchingPreferences;
import com.tinder.profiles.infrastructure.persistence.preferences.PreferencesDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Translates inbound API DTOs into application commands, applying sanitization at
 * the boundary and converting {@link PreferencesDto} into the self-validating
 * {@link MatchingPreferences} value object (its constructor enforces
 * {@code minAge <= maxAge}).
 */
@Component
@RequiredArgsConstructor
public class ProfileApiMapper {

    private final TextSanitizerPort sanitizer;

    public CreateProfileCommand toCreateCommand(CreateProfileDtoV1 dto, String userId) {
        return new CreateProfileCommand(
                userId,
                sanitizer.sanitizePlainText(dto.name()),
                dto.age(),
                sanitizer.sanitizePlainText(dto.gender()),
                sanitizer.sanitizePlainText(dto.bio()),
                sanitizer.sanitizePlainText(dto.city()),
                toPreferences(dto.preferences()),
                dto.hobbies(),
                dto.latitude(),
                dto.longitude());
    }

    public UpdateProfileCommand toUpdateCommand(CreateProfileDtoV1 dto, String userId) {
        return new UpdateProfileCommand(
                userId,
                sanitizer.sanitizePlainText(dto.name()),
                dto.age(),
                sanitizer.sanitizePlainText(dto.gender()),
                sanitizer.sanitizePlainText(dto.bio()),
                sanitizer.sanitizePlainText(dto.city()),
                toPreferences(dto.preferences()),
                dto.hobbies(),
                dto.latitude(),
                dto.longitude());
    }

    public PatchProfileCommand toPatchCommand(PatchProfileDto dto, String userId) {
        return new PatchProfileCommand(
                userId,
                sanitizer.sanitizePlainText(dto.name()),
                dto.age(),
                sanitizer.sanitizePlainText(dto.gender()),
                sanitizer.sanitizePlainText(dto.bio()),
                sanitizer.sanitizePlainText(dto.city()),
                toPreferences(dto.preferences()),
                dto.hobbies(),
                dto.latitude(),
                dto.longitude());
    }

    private MatchingPreferences toPreferences(PreferencesDto p) {
        if (p == null) {
            return null;
        }
        return new MatchingPreferences(p.getMinAge(), p.getMaxAge(), p.getGender(), p.getMaxRange());
    }
}
