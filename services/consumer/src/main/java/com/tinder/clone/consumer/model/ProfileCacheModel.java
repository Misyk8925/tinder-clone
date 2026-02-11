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

    @Column(nullable = false)
    private Instant createdAt;
}
