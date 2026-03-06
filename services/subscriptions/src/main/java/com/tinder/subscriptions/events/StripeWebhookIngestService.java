package com.tinder.subscriptions.events;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.tinder.subscriptions.stripeServices.StripeConfig;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class StripeWebhookIngestService {

    private final StripeConfig stripeConfig;
    private final StripeEventInboxRepository stripeEventInboxRepository;

    @Transactional
    public void ingestEvent(String payload, String signature) throws SignatureVerificationException {

        log.info("Processing event: {}", payload);
        Event event = Webhook.constructEvent(
                payload,
                signature,
                stripeConfig.getWebhookSecret()
        );

        log.info("Event created: {}", event.getCreated());

        if (stripeEventInboxRepository.existsById(event.getId())) {
            return;
        }

        StripeEventInbox stripeEventInbox = StripeEventInbox.builder()
                .id(event.getId())
                .eventType(event.getType())
                .stripeCreated(event.getCreated())
                .livemode(Boolean.TRUE.equals(event.getLivemode()))
                .payloadJson(payload)
                .status(StripeEventInbox.Status.PENDING)
                .attempts(0)
                .nextRetryAt(Instant.now())
                .build();

        log.info("Saving event: {}", stripeEventInbox);
        stripeEventInboxRepository.save(stripeEventInbox);

    }

}
