package com.tinder.profiles.preferences;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PreferencesRepository extends JpaRepository<Preferences, UUID> {
}
