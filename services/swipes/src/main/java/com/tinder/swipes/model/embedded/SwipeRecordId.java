package com.tinder.swipes.model.embedded;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Embeddable
@NoArgsConstructor
public class SwipeRecordId {
    private UUID profile1Id;
    private UUID profile2Id;

    public SwipeRecordId(UUID profile1Id, UUID profile2Id) {
        this.profile1Id = profile1Id;
        this.profile2Id = profile2Id;
    }
}
