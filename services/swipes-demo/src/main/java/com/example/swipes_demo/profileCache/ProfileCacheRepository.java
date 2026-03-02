package com.example.swipes_demo.profileCache;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface ProfileCacheRepository extends JpaRepository<ProfileCache, UUID> {
    long countByProfileIdIn(Collection<UUID> profileIds);
}
