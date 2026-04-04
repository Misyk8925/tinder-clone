package com.tinder.clone.consumer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "swipe_events"
)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
/**
 * @deprecated Kept for historical data only. Use the new swipe model for all new development.
 */
@Deprecated
public class SwipeEvent implements Serializable {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "swiper_id", nullable = false)
    private UUID swiperId;

    @Column(name = "swiped_id", nullable = false)
    private UUID swipedId;

    @Column(name = "decision", nullable = false)
    private Boolean decision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
