package com.tinder.clone.consumer.repository;



import com.tinder.clone.consumer.model.ProfileCacheModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProfileCacheRepository extends JpaRepository<ProfileCacheModel, UUID> {
}
