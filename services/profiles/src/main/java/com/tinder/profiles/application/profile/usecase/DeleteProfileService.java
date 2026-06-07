package com.tinder.profiles.application.profile.usecase;

import com.tinder.profiles.application.profile.command.DeleteProfileCommand;
import com.tinder.profiles.application.profile.exception.ProfileNotFoundException;
import com.tinder.profiles.application.profile.port.in.DeleteProfileUseCase;
import com.tinder.profiles.application.profile.port.out.DomainEventPublisherPort;
import com.tinder.profiles.application.profile.port.out.ProfileCachePort;
import com.tinder.profiles.application.profile.port.out.ProfileRepositoryPort;
import com.tinder.profiles.domain.profile.Profile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteProfileService implements DeleteProfileUseCase {

    private final ProfileRepositoryPort profiles;
    private final ProfileCachePort cache;
    private final DomainEventPublisherPort events;

    @Override
    @Transactional
    public void handle(DeleteProfileCommand cmd) {
        Profile existing = profiles.findByUserId(cmd.userId())
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ProfileNotFoundException(cmd.userId()));

        UUID id = existing.getId();
        if (existing.isDeletable()) {
            existing.markAsDeleted();
            profiles.save(existing);
            cache.evict(id);
            cache.evictReadModels(cmd.userId(), id);
            log.info("Profile deleted successfully: {}", id);
        }
        events.publishDeleted(id);
    }
}
