package com.tinder.profiles.application.profile.usecase;

import com.tinder.profiles.application.profile.command.PatchProfileCommand;
import com.tinder.profiles.application.profile.exception.PatchOperationException;
import com.tinder.profiles.application.profile.exception.ProfileNotFoundException;
import com.tinder.profiles.application.profile.port.in.PatchProfileUseCase;
import com.tinder.profiles.application.profile.port.out.DomainEventPublisherPort;
import com.tinder.profiles.application.profile.port.out.ProfileCachePort;
import com.tinder.profiles.application.profile.port.out.ProfileRepositoryPort;
import com.tinder.profiles.application.profile.port.out.ResolvedLocation;
import com.tinder.profiles.application.profile.support.LocationResolutionService;
import com.tinder.profiles.domain.profile.GeoPoint;
import com.tinder.profiles.domain.profile.Profile;
import com.tinder.profiles.domain.profile.ProfileChangeSet;
import com.tinder.profiles.domain.profile.ProfileDomainService;
import com.tinder.profiles.domain.profile.ProfileEdit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatchProfileService implements PatchProfileUseCase {

    private final ProfileRepositoryPort profiles;
    private final LocationResolutionService locations;
    private final DomainEventPublisherPort events;
    private final ProfileCachePort cache;
    private final ProfileDomainService domainService;

    @Value("${location.change.threshold-km:1.0}")
    double locationChangeThresholdKm;

    @Override
    @Transactional
    public UUID handle(PatchProfileCommand cmd) {
        Profile existing = profiles.findByUserId(cmd.userId())
                .orElseThrow(() -> new ProfileNotFoundException(cmd.userId()));

        if (!cmd.hasAnyField()) {
            throw PatchOperationException.noFieldsProvided();
        }

        // Sparse edit: only non-null fields are considered changes.
        GeoPoint requested = GeoPoint.of(cmd.latitude(), cmd.longitude()).orElse(null);
        ProfileEdit edit = new ProfileEdit(cmd.name(), cmd.age(), cmd.gender(), cmd.bio(),
                cmd.city(), requested, cmd.preferences(), cmd.hobbies());
        ProfileChangeSet changes = domainService.detectChanges(existing, edit);

        // Apply provided scalar fields, keeping current values for the rest.
        existing.updateBasicInfo(
                cmd.name() != null ? cmd.name() : existing.getName(),
                cmd.age() != null ? cmd.age() : existing.getAge(),
                cmd.gender() != null ? cmd.gender() : existing.getGender(),
                cmd.bio() != null ? cmd.bio() : existing.getBio(),
                cmd.city() != null ? cmd.city() : existing.getCity());

        // Location: city changed or GPS provided; coordinate-only updates suppress jitter.
        boolean hasCoords = cmd.latitude() != null && cmd.longitude() != null;
        boolean cityChanged = changes.has("city");
        if (cityChanged || hasCoords) {
            boolean moved = !hasCoords
                    || existing.hasMovedBeyond(new GeoPoint(cmd.latitude(), cmd.longitude()), locationChangeThresholdKm);
            if (cityChanged || moved) {
                String cityForLocation = cmd.city() != null ? cmd.city() : existing.getCity();
                ResolvedLocation resolved = locations.resolve(cmd.latitude(), cmd.longitude(), cityForLocation);
                existing.relocate(resolved.position(), resolved.city());
                changes.add("city");
            } else {
                log.debug("Ignoring coordinate update for profile {}: moved less than {}km",
                        existing.getId(), locationChangeThresholdKm);
            }
        }

        if (cmd.preferences() != null) {
            existing.changePreferences(cmd.preferences());
        }
        if (cmd.hobbies() != null) {
            existing.replaceHobbies(cmd.hobbies());
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
