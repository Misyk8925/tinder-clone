package com.tinder.subscriptions.events;

import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.tinder.subscriptions.grpc.SubscriptionGrpcClient;
import com.tinder.subscriptions.stripeCustomer.StripeCustomer;
import com.tinder.subscriptions.stripeCustomer.StripeCustomerRepository;
import com.tinder.subscriptions.subscription.BillingSubscription;
import com.tinder.subscriptions.subscription.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.stripe.net.ApiResource.GSON;

@Service
@Slf4j
@RequiredArgsConstructor
public class StripeWebhookProcessService {

    private static final int MAX_ATTEMPTS = 10;
    private static final long PROCESSING_LEASE_SECONDS = 600;
    private static final int LAST_ERROR_MAX_LENGTH = 255;

    private final StripeEventInboxRepository stripeEventInboxRepository;
    private final StripeCustomerRepository stripeCustomerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionGrpcClient subscriptionGrpcClient;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = 5000)
    public void processBatch() {
        log.info("Processing batch of Stripe events");
        List<StripeEventInbox> batch = claimBatch();

        for (StripeEventInbox row : batch) {
            processSingleEvent(row);
        }
    }

    List<StripeEventInbox> claimBatch() {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            List<StripeEventInbox> rows = stripeEventInboxRepository.claimProcessableBatch(now);
            rows.forEach(row -> {
                row.setStatus(StripeEventInbox.Status.PROCESSING);
                row.setNextRetryAt(now.plusSeconds(PROCESSING_LEASE_SECONDS));
            });
            return stripeEventInboxRepository.saveAll(rows);
        });
    }

    void processSingleEvent(StripeEventInbox row) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Event event = GSON.fromJson(row.getPayloadJson(), Event.class);
                handleEvent(event);
                row.setStatus(StripeEventInbox.Status.PROCESSED);
                row.setProcessedAt(Instant.now());
                stripeEventInboxRepository.save(row);
            });
        } catch (Exception e) {
            log.warn("Failed to process event {}: {}", row.getId(), e.getMessage());
            transactionTemplate.executeWithoutResult(status -> {
                StripeEventInbox freshRow = stripeEventInboxRepository.findById(row.getId()).orElseThrow();
                int attempts = freshRow.getAttempts() + 1;
                freshRow.setAttempts(attempts);
                freshRow.setLastError(truncateError(e));
                if (attempts >= MAX_ATTEMPTS) {
                    freshRow.setStatus(StripeEventInbox.Status.FAILED);
                    freshRow.setNextRetryAt(null);
                    stripeEventInboxRepository.save(freshRow);
                    return;
                }
                freshRow.setStatus(StripeEventInbox.Status.PENDING);
                // Short retry for dependency ordering issues, longer for repeated failures
                long retryDelaySec = freshRow.getAttempts() < 3 ? 5 : 60;
                freshRow.setNextRetryAt(Instant.now().plusSeconds(retryDelaySec));
                stripeEventInboxRepository.save(freshRow);
            });
        }
    }

    private String truncateError(Exception error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return message.substring(0, Math.min(message.length(), LAST_ERROR_MAX_LENGTH));
    }

    private void handleEvent(Event event) {
        switch (event.getType()) {
            case "checkout.session.completed",
                 "checkout.session.async_payment_succeeded" -> onCheckoutCompleted(event);

            case "customer.subscription.created",
                 "customer.subscription.updated",
                 "customer.subscription.deleted" -> onSubscriptionChanged(event);

            default -> log.warn("Unsupported event type: {}", event.getType());
        }
    }

    private void onCheckoutCompleted(Event event) {

        Session session = (Session) event.getDataObjectDeserializer().getObject().orElseThrow();
        synchronizeCheckoutCustomer(session, Boolean.TRUE.equals(event.getLivemode()));
    }

    void synchronizeCheckoutCustomer(Session session, boolean livemode) {
        String userId = session.getClientReferenceId();
        String customerId = session.getCustomer();

        StripeCustomer customer = stripeCustomerRepository.findByStripeCustomerId(customerId)
                .orElseGet(() -> {
                    StripeCustomer c = new StripeCustomer();
                    c.setId(customerId);
                    c.setCreatedAt(Instant.now());
                    return c;
                });
        customer.setUserId(userId);
        customer.setStripeCustomerId(customerId);
        customer.setLivemode(livemode);
        customer.setUpdatedAt(Instant.now());
        stripeCustomerRepository.save(customer);

        // Subscription events are authoritative for entitlement state and paid-through time.
        // Checkout completion only establishes the Stripe customer-to-user mapping.
    }

    private void onSubscriptionChanged(Event event) {

        Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElseThrow();

        synchronizeSubscription(subscription, event.getCreated());
    }

    void synchronizeSubscription(Subscription subscription, long stripeEventCreated) {
        StripeCustomer stripeCustomer = stripeCustomerRepository
                .findByStripeCustomerId(subscription.getCustomer())
                .orElse(null);

        if (stripeCustomer == null) {
            throw new IllegalStateException(
                    "StripeCustomer not found for customerId=" + subscription.getCustomer()
                    + ". checkout.session.completed may not have been processed yet. Will retry.");
        }

        BillingSubscription row = subscriptionRepository
                .findById(subscription.getId())
                .orElseGet(BillingSubscription::new);

        if (row.getLastStripeEventCreated() != null && stripeEventCreated < row.getLastStripeEventCreated()) {
            return; // out-of-order old event
        }

        SubscriptionItem subscriptionItem = firstSubscriptionItem(subscription);
        Instant currentPeriodEnd = subscriptionItem == null || subscriptionItem.getCurrentPeriodEnd() == null
                ? null
                : Instant.ofEpochSecond(subscriptionItem.getCurrentPeriodEnd());

        row.setStripeSubscriptionId(subscription.getId());
        row.setStripeCustomerId(subscription.getCustomer());
        row.setUserId(stripeCustomer.getUserId());
        row.setPriceId(subscriptionItem == null || subscriptionItem.getPrice() == null
                ? null : subscriptionItem.getPrice().getId());
        row.setStatus(subscription.getStatus());
        row.setCurrentPeriodEnd(currentPeriodEnd);
        row.setCancelAtPeriodEnd(Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()));
        row.setLastStripeEventCreated(stripeEventCreated);
        row.setUpdatedAt(Instant.now());
        subscriptionRepository.save(row);

        boolean premium = Set.of("active", "trialing").contains(subscription.getStatus());

        if (premium) {
            if (currentPeriodEnd == null) {
                throw new IllegalStateException("Current period end missing for active subscription " + subscription.getId());
            }
            log.info("Activating premium for userId={} until={}", stripeCustomer.getUserId(), currentPeriodEnd);
            subscriptionGrpcClient.activatePremiumUntil(stripeCustomer.getUserId(), currentPeriodEnd);
        } else {
            log.info("Revoking premium for userId={} because subscription status={}",
                    stripeCustomer.getUserId(), subscription.getStatus());
            subscriptionGrpcClient.revokePremium(stripeCustomer.getUserId());
        }
    }

    private SubscriptionItem firstSubscriptionItem(Subscription subscription) {
        if (subscription.getItems() == null || subscription.getItems().getData() == null
                || subscription.getItems().getData().isEmpty()) {
            return null;
        }
        return subscription.getItems().getData().getFirst();
    }

}
