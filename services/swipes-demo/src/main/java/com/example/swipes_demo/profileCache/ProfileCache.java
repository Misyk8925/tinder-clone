package com.example.swipes_demo.profileCache;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profile_cache")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfileCache {

    @Id
    private UUID profileId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Instant createdAt;

}
