package com.tinder.profiles.application.profile.usecase;

import com.tinder.profiles.application.profile.command.DeleteProfilesCommand;
import com.tinder.profiles.application.profile.port.out.ProfileRepositoryPort;
import com.tinder.profiles.application.profile.support.ProfileRetentionPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Permanently removes profiles whose soft-delete retention window has passed.
 * Owns the retention rule; the scheduled trigger only decides <em>when</em> to ask.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PurgeSoftDeletedProfilesService {

    private final ProfileRepositoryPort profiles;
    private final DeleteProfilesService deleteProfiles;
    private final ProfileRetentionPolicy retention;

    /** @return the number of profiles purged. */
    @Transactional
    public int handle() {
        LocalDateTime cutoff = retention.cutoffFrom(LocalDateTime.now());
        List<UUID> stale = profiles.findSoftDeletedBefore(cutoff);

        if (stale.isEmpty()) {
            log.debug("No stale deleted profiles found for purging");
            return 0;
        }

        log.info("Purging {} profile(s) soft-deleted before {}", stale.size(), cutoff);
        deleteProfiles.handle(new DeleteProfilesCommand(stale));
        return stale.size();
    }
}
