package com.tinder.profiles.application.profile.usecase;

import com.tinder.profiles.application.profile.command.PatchProfileCommand;
import com.tinder.profiles.application.profile.exception.PatchOperationException;
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
public class PatchProfileService {

    private final ProfileRepositoryPort profiles;
    private final LocationPort location;
    private final DomainEventPublisherPort events;
    private final ProfileCachePort cache;
    private final ProfileEditService editService;
    private final LocationChangePolicy locationChangePolicy;

    @Transactional
    public UUID handle(PatchProfileCommand cmd) {
        Profile existing = profiles.findByUserId(cmd.userId())
                .orElseThrow(() -> new ProfileNotFoundException(cmd.userId()));

        if (!cmd.hasAnyField()) {
            throw PatchOperationException.noFieldsProvided();
        }

        // Sparse edit: only non-null fields are considered changes.
        ProfileEdit edit = editService.toEdit(cmd);
        ProfileChangeSet changes = editService.detectChanges(existing, edit);

        // Apply provided scalar fields, keeping current values for the rest.
        existing.updateBasicInfo(
                edit.name() != null ? edit.name() : existing.getName(),
                edit.age() != null ? edit.age() : existing.getAge(),
                edit.gender() != null ? edit.gender() : existing.getGender(),
                edit.bio() != null ? edit.bio() : existing.getBio(),
                edit.city() != null ? edit.city() : existing.getCity());

        // Location: city changed or GPS provided; coordinate-only updates suppress jitter.
        boolean hasCoords = cmd.latitude() != null && cmd.longitude() != null;
        boolean cityChanged = changes.has("city");
        if (cityChanged || hasCoords) {
            boolean moved = !hasCoords
                    || locationChangePolicy.movedSignificantly(
                            existing, new GeoPoint(cmd.latitude(), cmd.longitude()));
            if (cityChanged || moved) {
                String cityForLocation = edit.city() != null ? edit.city() : existing.getCity();
                ResolvedLocation resolved = location.resolve(cmd.latitude(), cmd.longitude(), cityForLocation);
                existing.relocate(resolved.position(), resolved.city());
                changes.add("city");
            } else {
                log.debug("Ignoring coordinate update for profile {}: moved less than {}km",
                        existing.getId(), locationChangePolicy.thresholdKm());
            }
        }

        if (edit.preferences() != null) {
            existing.changePreferences(edit.preferences());
        }
        if (edit.hobbies() != null) {
            existing.replaceHobbies(edit.hobbies());
        }

        Profile saved = profiles.save(existing);

        // Only publish when something effectively changed (suppressed GPS jitter → no event).
        if (!changes.isEmpty()) {
            events.publishUpdated(saved.getId(), changes.classify(), changes.changedFields());
            log.info("Profile patched for userId: {} changeType={} fields={}",
                    cmd.userId(), changes.classify(), changes.changedFields());
        } else {
            log.debug("No effective changes for patch on profile {} (coordinate jitter suppressed)", cmd.userId());
        }

        cache.refreshOnWrite(cmd.userId(), saved);
        return saved.getId();
    }
}
