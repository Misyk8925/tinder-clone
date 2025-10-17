package com.tinder.profiles.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    Profile findByName(String username);
    Profile findByProfileIdString(String profileIdString);

    List<Profile> findAllByIsDeletedFalse();
}