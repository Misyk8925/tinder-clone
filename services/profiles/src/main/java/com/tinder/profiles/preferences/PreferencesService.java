package com.tinder.profiles.preferences;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class PreferencesService {

    private final PreferencesRepository repo;

    @Transactional
    public Preferences findOrCreate(PreferencesDto preferences) {
        Integer minAge = preferences.getMinAge();
        Integer maxAge = preferences.getMaxAge();
        String gender = preferences.getGender();
        Integer maxRange = preferences.getMaxRange() != null ? preferences.getMaxRange() : 50;

        // Try to find existing preferences with the same values
        Optional<Preferences> existing = repo.findByValues(minAge, maxAge, gender, maxRange);

        if (existing.isPresent()) {
            log.debug("Reusing existing preferences: minAge={}, maxAge={}, gender={}, maxRange={}",
                     minAge, maxAge, gender, maxRange);
            return existing.get();
        }

        // Create new preferences only if none exist
        Preferences newPreferences = Preferences.builder()
                .minAge(minAge)
                .maxAge(maxAge)
                .gender(gender)
                .maxRange(maxRange)
                .build();

        Preferences saved = repo.save(newPreferences);
        log.info("Created new preferences: id={}, minAge={}, maxAge={}, gender={}, maxRange={}",
                saved.getId(), minAge, maxAge, gender, maxRange);
        return saved;
    }
}
