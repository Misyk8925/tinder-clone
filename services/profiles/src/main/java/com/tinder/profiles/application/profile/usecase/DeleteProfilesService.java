package com.tinder.profiles.application.profile.usecase;

import com.tinder.profiles.application.profile.command.DeleteProfilesCommand;
import com.tinder.profiles.application.profile.port.out.ProfileCachePort;
import com.tinder.profiles.application.profile.port.out.ProfileRepositoryPort;
import com.tinder.profiles.domain.profile.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DeleteProfilesService {

    private final ProfileRepositoryPort profiles;
    private final ProfileCachePort cache;

    @Transactional
    public void handle(DeleteProfilesCommand cmd) {
        List<String> userIds = profiles.findAllById(cmd.ids()).stream()
                .map(Profile::getUserId)
                .filter(Objects::nonNull)
                .toList();

        profiles.deleteAllById(cmd.ids());
        cache.evictBatch(cmd.ids(), userIds);
    }
}
