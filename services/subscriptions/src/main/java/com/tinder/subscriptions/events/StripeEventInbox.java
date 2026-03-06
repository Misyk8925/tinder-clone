package com.tinder.subscriptions.events;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "stripe_event_inbox")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeEventInbox {

    @Id
    private String id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    private String objectId;
    private Long stripeCreated;
    private boolean livemode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    private Status status;

    private int attempts;
    private String lastError;
    private Instant nextRetryAt;
    private Instant processedAt;

    public enum Status { PENDING, PROCESSED, FAILED }

}
