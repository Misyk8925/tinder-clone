package com.tinder.swipes.repository;

import com.tinder.swipes.model.ProfileCacheModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProfileCacheRepository extends JpaRepository<ProfileCacheModel, UUID> {
}
