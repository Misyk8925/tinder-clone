package com.tinder.swipes.model;


import com.tinder.swipes.model.embedded.SwipeRecordId;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;


@Entity
@Table(name = "swipe_records")
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
