package com.tinder.swipes.model;


import com.tinder.swipes.model.embedded.SwipeRecordId;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.UUID;


@Entity
@Table(name = "swipe_records")
@AllArgsConstructor
@NoArgsConstructor
public class SwipeRecord {

    @EmbeddedId
    private SwipeRecordId swipeRecordId;



    @Column
    private Boolean decision1;



    @Column
    private Boolean decision2;

    public void setDecision2(Boolean decision1) {
        this.decision1 = decision1;
    }

    public Boolean getDecision2() {
        return decision2;
    }

    public Boolean getDecision1() {
        return decision1;
    }

    public void setDecision1(Boolean decision1) {
        this.decision1 = decision1;
    }
}
