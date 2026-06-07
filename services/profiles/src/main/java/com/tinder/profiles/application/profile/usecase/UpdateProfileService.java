package com.tinder.profiles.application.profile.usecase;

import com.tinder.profiles.application.profile.command.UpdateProfileCommand;
import com.tinder.profiles.application.profile.exception.ProfileNotFoundException;
import com.tinder.profiles.application.profile.port.in.UpdateProfileUseCase;
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
public class UpdateProfileService implements UpdateProfileUseCase {

    private final ProfileRepositoryPort profiles;
    private final LocationResolutionService locations;
    private final DomainEventPublisherPort events;
    private final ProfileCachePort cache;
    private final ProfileDomainService domainService;

    @Value("${location.change.threshold-km:1.0}")
    double locationChangeThresholdKm;

    @Override
    @Transactional
    public UUID handle(UpdateProfileCommand cmd) {
        Profile existing = profiles.findByUserId(cmd.userId())
                .orElseThrow(() -> new ProfileNotFoundException(cmd.userId()));

        GeoPoint requested = GeoPoint.of(cmd.latitude(), cmd.longitude()).orElse(null);
        ProfileEdit edit = new ProfileEdit(cmd.name(), cmd.age(), cmd.gender(), cmd.bio(),
                cmd.city(), requested, cmd.preferences(), cmd.hobbies());
        domainService.requireLocationProvided(edit);

        ProfileChangeSet changes = domainService.detectChanges(existing, edit);

        // Full update of the editable basic info; blank city falls back to current.
        String effectiveCity = (cmd.city() != null && !cmd.city().isBlank()) ? cmd.city() : existing.getCity();
        existing.updateBasicInfo(cmd.name(), cmd.age(), cmd.gender(), cmd.bio(), effectiveCity);

        resolveLocationIfNeeded(existing, changes, cmd.latitude(), cmd.longitude(), effectiveCity);

        if (cmd.preferences() != null) {
            existing.changePreferences(cmd.preferences());
        }
        if (cmd.hobbies() != null) {
            existing.replaceHobbies(cmd.hobbies());
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
        boolean moved = !hasCoords || existing.hasMovedBeyond(new GeoPoint(lat, lon), locationChangeThresholdKm);
        if (cityChanged || moved) {
            ResolvedLocation resolved = locations.resolve(lat, lon, effectiveCity);
            existing.relocate(resolved.position(), resolved.city());
            changes.add("city");
        }
    }
}
