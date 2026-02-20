package com.tinder.match.match.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MatchChatAnalyticsId implements Serializable {

    @Column(name = "profile1_id", nullable = false)
    private UUID profile1Id;

    @Column(name = "profile2_id", nullable = false)
    private UUID profile2Id;

    public static MatchChatAnalyticsId normalized(UUID firstProfileId, UUID secondProfileId) {
        if (firstProfileId == null || secondProfileId == null) {
            throw new IllegalArgumentException("Profile ids must not be null");
        }
        if (firstProfileId.equals(secondProfileId)) {
            throw new IllegalArgumentException("Profile ids must be different");
        }
        if (firstProfileId.compareTo(secondProfileId) <= 0) {
            return new MatchChatAnalyticsId(firstProfileId, secondProfileId);
        }
        return new MatchChatAnalyticsId(secondProfileId, firstProfileId);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MatchChatAnalyticsId that)) {
            return false;
        }
        return Objects.equals(profile1Id, that.profile1Id) && Objects.equals(profile2Id, that.profile2Id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profile1Id, profile2Id);
    }
}
