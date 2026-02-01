package com.tinder.swipes.model;


import com.tinder.swipes.model.embedded.SwipeRecordId;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;


@Entity
@Table(name = "swipe_records", indexes = {
        @Index(name = "idx_swipe_profile1_id",
                columnList = "profile1_id"),
        @Index(name = "idx_swipe_profile2_id",
                columnList = "profile2_id"),
        @Index(name = "idx_swipe_profile1_decision",
                columnList = "profile1_id, decision1"),
        @Index(name = "idx_swipe_profile2_decision",
                columnList = "profile2_id, decision2"),
        @Index(name = "idx_swipe_both_profiles",
                columnList = "profile1_id, profile2_id, decision1, decision2"),
})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class SwipeRecord implements Serializable {

    @EmbeddedId
    private SwipeRecordId swipeRecordId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column
    private Boolean decision1;

    @Column
    private Boolean decision2;
}
