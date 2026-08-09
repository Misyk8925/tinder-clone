package com.tinder.profiles.application.profile.usecase;

import com.tinder.profiles.application.profile.command.UpdateProfileCommand;
import com.tinder.profiles.application.profile.exception.ProfileNotFoundException;
import com.tinder.profiles.application.profile.model.ProfileEdit;
import com.tinder.profiles.application.profile.port.out.DomainEventPublisherPort;
import com.tinder.profiles.application.profile.port.out.LocationPort;
import com.tinder.profiles.application.profile.port.out.ProfileCachePort;
import com.tinder.profiles.application.profile.port.out.ProfileRepositoryPort;
import com.tinder.profiles.application.profile.port.out.ResolvedLocation;
import com.tinder.profiles.application.profile.support.LocationChangePolicy;
import com.tinder.profiles.application.profile.support.ProfileEditService;
import com.tinder.profiles.domain.profile.GeoPoint;
import com.tinder.profiles.domain.profile.Profile;
import com.tinder.profiles.domain.profile.ProfileChangeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateProfileService {

    private final ProfileRepositoryPort profiles;
    private final LocationPort location;
    private final DomainEventPublisherPort events;
    private final ProfileCachePort cache;
    private final ProfileEditService editService;
    private final LocationChangePolicy locationChangePolicy;

    @Transactional
    public UUID handle(UpdateProfileCommand cmd) {
        Profile existing = profiles.findByUserId(cmd.userId())
                .orElseThrow(() -> new ProfileNotFoundException(cmd.userId()));

        ProfileEdit edit = editService.toEdit(cmd);
        editService.requireLocationProvided(edit);

        ProfileChangeSet changes = editService.detectChanges(existing, edit);

        // Full update of the editable basic info; blank city falls back to current.
        String effectiveCity = edit.hasCity() ? edit.city() : existing.getCity();
        existing.updateBasicInfo(edit.name(), edit.age(), edit.gender(), edit.bio(), effectiveCity);

        resolveLocationIfNeeded(existing, changes, cmd.latitude(), cmd.longitude(), effectiveCity);

        if (edit.preferences() != null) {
            existing.changePreferences(edit.preferences());
        }
        if (edit.hobbies() != null) {
            existing.replaceHobbies(edit.hobbies());
        }

        Profile saved = profiles.save(existing);
        events.publishUpdated(saved.getId(), changes.classify(), changes.changedFields());
        cache.refreshOnWrite(cmd.userId(), saved);

        log.info("Profile updated for userId: {} changeType={} fields={}",
                cmd.userId(), changes.classify(), changes.changedFields());
        return saved.getId();
    }

    private void resolveLocationIfNeeded(Profile existing, ProfileChangeSet changes,
                                         Double lat, Double lon, String effectiveCity) {
        boolean hasCoords = lat != null && lon != null;
        boolean cityChanged = changes.has("city");
        if (!cityChanged && !hasCoords) {
            return;
        }
        boolean moved = !hasCoords
                || locationChangePolicy.movedSignificantly(existing, new GeoPoint(lat, lon));
        if (cityChanged || moved) {
            ResolvedLocation resolved = location.resolve(lat, lon, effectiveCity);
            existing.relocate(resolved.position(), resolved.city());
            changes.add("city");
        }
    }
}
