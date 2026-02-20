package com.tinder.match.match.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "matches")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Match {

    @EmbeddedId
    private MatchId id;

    @Enumerated
    @Column(name = "status", nullable = false)
    private MatchStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "matched_at", nullable = false, updatable = false)
    private Instant matchedAt;

    @Column(name = "unmatched_at")
    private Instant unmatchedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;


}
