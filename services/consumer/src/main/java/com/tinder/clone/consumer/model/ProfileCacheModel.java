package com.tinder.clone.consumer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class ProfileCacheModel {

    @Id
    private UUID profileId;

    /** Keycloak user ID — used to resolve profileId from JWT sub in the liked-me endpoint. */
    @Column(name = "user_id", unique = true)
    private String userId;

    @Column(nullable = false)
    private Instant createdAt;
}
