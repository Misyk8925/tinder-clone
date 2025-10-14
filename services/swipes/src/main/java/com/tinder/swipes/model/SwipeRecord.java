package com.tinder.swipes.model;


import com.tinder.swipes.model.embedded.SwipeRecordId;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;


@Entity
@Table(name = "swipe_records")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SwipeRecord {

    @EmbeddedId
    private SwipeRecordId swipeRecordId;

    @Column
    private Boolean decision1;

    @Column
    private Boolean decision2;
}
