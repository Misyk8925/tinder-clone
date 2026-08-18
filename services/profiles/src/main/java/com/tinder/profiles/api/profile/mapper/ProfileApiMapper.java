package com.tinder.profiles.api.profile.mapper;

import com.tinder.contracts.dto.Hobby;
import com.tinder.contracts.dto.SharedLocationDto;
import com.tinder.contracts.dto.SharedPhotoDto;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.profiles.api.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.api.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.api.profile.dto.profileData.PatchProfileDto;
import com.tinder.profiles.api.profile.dto.profileData.PhotoDto;
import com.tinder.profiles.api.profile.dto.profileData.PreferencesDto;
import com.tinder.profiles.application.profile.command.CreateProfileCommand;
import com.tinder.profiles.application.profile.command.PatchProfileCommand;
import com.tinder.profiles.application.profile.command.UpdateProfileCommand;
import com.tinder.profiles.application.profile.model.PreferencesData;
import com.tinder.profiles.application.profile.query.InternalProfileView;
import com.tinder.profiles.application.profile.query.ProfileView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates inbound API DTOs into application commands and application query
 * views back into response DTOs.
 *
 * <p>A pure translator: it speaks only transport types and the application's own
 * vocabulary — commands, views and {@link PreferencesData}. Sanitizing the text,
 * validating the values and building domain value objects all happen inside the
 * application layer.
 */
@Slf4j
@Component
public class ProfileApiMapper {

    public CreateProfileCommand toCreateCommand(CreateProfileDtoV1 dto, String userId) {
        return new CreateProfileCommand(
                userId,
                dto.name(),
                dto.age(),
                dto.gender(),
                dto.bio(),
                dto.city(),
                toPreferences(dto.preferences()),
                toHobbyNames(dto.hobbies()),
                dto.latitude(),
                dto.longitude());
    }

    public UpdateProfileCommand toUpdateCommand(CreateProfileDtoV1 dto, String userId) {
        return new UpdateProfileCommand(
                userId,
                dto.name(),
                dto.age(),
                dto.gender(),
                dto.bio(),
                dto.city(),
                toPreferences(dto.preferences()),
                toHobbyNames(dto.hobbies()),
                dto.latitude(),
                dto.longitude());
    }

    public PatchProfileCommand toPatchCommand(PatchProfileDto dto, String userId) {
        return new PatchProfileCommand(
                userId,
                dto.name(),
                dto.age(),
                dto.gender(),
                dto.bio(),
                dto.city(),
                toPreferences(dto.preferences()),
                toHobbyNames(dto.hobbies()),
                dto.latitude(),
                dto.longitude());
    }

    public GetProfileDto toGetProfileDto(ProfileView view) {
        if (view == null) {
            return null;
        }
        PreferencesDto preferences = view.preferences() == null
                ? null
                : new PreferencesDto(
                        view.preferences().minAge(),
                        view.preferences().maxAge(),
                        view.preferences().gender(),
                        view.preferences().maxRange());
        List<PhotoDto> photos = view.photos().stream()
                .map(photo -> new PhotoDto(
                        photo.photoId(),
                        photo.s3Key(),
                        photo.primary(),
                        photo.position(),
                        photo.url(),
                        photo.contentType(),
                        photo.size(),
                        photo.createdAt()))
                .toList();
        return new GetProfileDto(
                view.profileId(),
                view.userId(),
                view.name(),
                view.age(),
                view.gender(),
                view.bio(),
                view.city(),
                view.active(),
                view.createdAt(),
                preferences,
                photos,
                toContractHobbies(view.hobbies()));
    }

    public SharedProfileDto toSharedProfileDto(InternalProfileView view) {
        SharedLocationDto location = view.location() == null
                ? null
                : new SharedLocationDto(
                        view.location().id(),
                        view.location().latitude(),
                        view.location().longitude(),
                        view.location().city(),
                        view.location().createdAt(),
                        view.location().updatedAt());
        SharedPreferencesDto preferences = view.preferences() == null
                ? null
                : new SharedPreferencesDto(
                        view.preferences().minAge(),
                        view.preferences().maxAge(),
                        view.preferences().gender(),
                        view.preferences().maxRange());
        List<SharedPhotoDto> photos = view.photos().stream()
                .map(photo -> new SharedPhotoDto(
                        photo.photoId(),
                        photo.profileId(),
                        photo.s3Key(),
                        photo.primary(),
                        photo.position(),
                        photo.url(),
                        photo.contentType(),
                        photo.size(),
                        photo.createdAt()))
                .toList();
        return new SharedProfileDto(
                view.id(),
                view.name(),
                view.age(),
                view.bio(),
                view.city(),
                view.active(),
                location,
                preferences,
                view.deleted(),
                photos,
                toContractHobbies(view.hobbies()));
    }

    private PreferencesData toPreferences(PreferencesDto p) {
        if (p == null) {
            return null;
        }
        return new PreferencesData(p.minAge(), p.maxAge(), p.gender(), p.maxRange());
    }

    private List<String> toHobbyNames(List<Hobby> hobbies) {
        return hobbies == null
                ? null
                : hobbies.stream().map(Enum::name).toList();
    }

    /**
     * Translates the view's hobby names into the contract enum. A name that no
     * longer matches a constant is dropped with a warning rather than failing the
     * response: these are reads, and the stored vocabulary outlives the enum.
     */
    private List<Hobby> toContractHobbies(List<String> hobbyNames) {
        if (hobbyNames == null) {
            return List.of();
        }
        List<Hobby> hobbies = new ArrayList<>(hobbyNames.size());
        for (String name : hobbyNames) {
            try {
                hobbies.add(Hobby.valueOf(name));
            } catch (IllegalArgumentException | NullPointerException e) {
                log.warn("Dropping unknown hobby '{}' from response", name);
            }
        }
        return List.copyOf(hobbies);
    }
}
