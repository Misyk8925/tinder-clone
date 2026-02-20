package com.tinder.match.match.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class MatchId {

    @Column(name = "profile1_id", nullable = false)
    private UUID profile1Id;

    @Column(name = "profile2_id", nullable = false)
    private UUID profile2Id;

    public static MatchId normalized(UUID firstProfileId, UUID secondProfileId) {
        if (firstProfileId == null || secondProfileId == null) {
            throw new IllegalArgumentException("Profile ids must not be null");
        }
        if (firstProfileId.equals(secondProfileId)) {
            throw new IllegalArgumentException("Profile ids must be different");
        }
        if (firstProfileId.compareTo(secondProfileId) <= 0) {
            return new MatchId(firstProfileId, secondProfileId);
        }
        return new MatchId(secondProfileId, firstProfileId);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MatchChatAnalyticsId that)) {
            return false;
        }
        return Objects.equals(profile1Id, that.getProfile1Id()) && Objects.equals(profile2Id, that.getProfile2Id());
    }

    @Override
    public int hashCode() {
        return Objects.hash(profile1Id, profile2Id);
    }
}