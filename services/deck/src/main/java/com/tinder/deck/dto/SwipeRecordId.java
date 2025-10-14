package com.tinder.deck.dto;


import lombok.NoArgsConstructor;

import java.util.UUID;


@NoArgsConstructor
public class SwipeRecordId {
    private UUID profile1Id;
    private UUID profile2Id;

    public SwipeRecordId(UUID profile1Id, UUID profile2Id) {
        this.profile1Id = profile1Id;
        this.profile2Id = profile2Id;
    }
}
