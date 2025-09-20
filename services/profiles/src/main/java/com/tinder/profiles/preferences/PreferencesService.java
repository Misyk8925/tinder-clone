package com.tinder.profiles.preferences;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PreferencesService {

    private final PreferencesRepository repo;

    public Preferences save(PreferencesDto preferences) {
        new Preferences();
        Preferences preferencesEntity = Preferences.builder()
                .minAge(preferences.getMinAge())
                .maxAge(preferences.getMaxAge())
                .gender(preferences.getGender())
                .maxRange(preferences.getMaxRange())
                .build();
        return repo.save(preferencesEntity);
    }

}
