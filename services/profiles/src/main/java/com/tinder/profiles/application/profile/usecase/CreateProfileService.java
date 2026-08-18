package com.tinder.profiles.application.profile.usecase;

import com.tinder.profiles.application.profile.command.CreateProfileCommand;
import com.tinder.profiles.application.profile.exception.ProfileAlreadyExistsException;
import com.tinder.profiles.application.profile.model.ProfileEdit;
import com.tinder.profiles.application.profile.port.out.DomainEventPublisherPort;
import com.tinder.profiles.application.profile.port.out.LocationPort;
import com.tinder.profiles.application.profile.port.out.ProfileCachePort;
import com.tinder.profiles.application.profile.port.out.ProfileRepositoryPort;
import com.tinder.profiles.application.profile.port.out.ResolvedLocation;
import com.tinder.profiles.application.profile.support.ProfileEditService;
import com.tinder.profiles.domain.profile.Profile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateProfileService {

    private final ProfileRepositoryPort profiles;
    private final LocationPort location;
    private final DomainEventPublisherPort events;
    private final ProfileCachePort cache;
    private final ProfileEditService editService;

    @Transactional
    public UUID handle(CreateProfileCommand cmd) {
        if (profiles.findByUserId(cmd.userId()).isPresent()) {
            throw new ProfileAlreadyExistsException(cmd.userId());
        }

        // Business rule: a profile must be locatable (city or coordinates), checked
        // against the originally-provided city before we default it.
        ProfileEdit edit = editService.toEdit(cmd);
        editService.requireLocationProvided(edit);

        String city = edit.hasCity() ? edit.city() : "Unknown";
        ResolvedLocation resolved = location.resolve(cmd.latitude(), cmd.longitude(), city);

        Profile profile = Profile.builder()
                .userId(cmd.userId())
                .name(edit.name())
                .age(edit.age())
                .gender(edit.gender())
                .bio(edit.bio())
                .city(resolved.city())
                .position(resolved.position())
                .active(true)
                .premium(false)
                .deleted(false)
                .preferences(edit.preferences())
                .hobbies(edit.hobbies())
                .build();

        Profile saved = profiles.save(profile);
        cache.refreshOnWrite(cmd.userId(), saved);
        events.publishCreated(saved.getId(), saved.getUserId());

        log.info("Profile created successfully for userId: {}", cmd.userId());
        return saved.getId();
    }
}
