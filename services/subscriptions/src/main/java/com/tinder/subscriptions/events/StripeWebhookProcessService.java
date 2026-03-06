package com.tinder.subscriptions.events;

import com.stripe.model.Event;
import com.stripe.model.Subscription;
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

    private final StripeEventInboxRepository stripeEventInboxRepository;
    private final StripeCustomerRepository stripeCustomerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionGrpcClient subscriptionGrpcClient;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = 5000)
    public void processBatch() {
        log.info("Processing batch of Stripe events");
        List<StripeEventInbox> batch = stripeEventInboxRepository.findTop50ByStatusAndNextRetryAtBeforeOrderByStripeCreatedAsc(
                StripeEventInbox.Status.PENDING, Instant.now());

        for (StripeEventInbox row : batch) {
            processSingleEvent(row);
        }
    }

    private void processSingleEvent(StripeEventInbox row) {
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
                freshRow.setAttempts(freshRow.getAttempts() + 1);
                freshRow.setLastError(e.getMessage());
                freshRow.setStatus(StripeEventInbox.Status.PENDING);
                // Short retry for dependency ordering issues, longer for repeated failures
                long retryDelaySec = freshRow.getAttempts() < 3 ? 5 : 60;
                freshRow.setNextRetryAt(Instant.now().plusSeconds(retryDelaySec));
                stripeEventInboxRepository.save(freshRow);
            });
        }
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
        customer.setLivemode(Boolean.TRUE.equals(event.getLivemode()));
        customer.setUpdatedAt(Instant.now());
        stripeCustomerRepository.save(customer);

        boolean isSubscriptionCheckout = "subscription".equalsIgnoreCase(session.getMode());
        boolean isPaid = Set.of("paid", "no_payment_required").contains(session.getPaymentStatus());
        if (isSubscriptionCheckout && isPaid && userId != null && !userId.isBlank()) {
            log.info("Checkout completed and paid. Updating premium status for userId: {}", userId);
            subscriptionGrpcClient.updatePremiumUser(userId);
        }
    }

    private void onSubscriptionChanged(Event event) {

        Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElseThrow();

        StripeCustomer stripeCustomer = stripeCustomerRepository
                .findByStripeCustomerId(subscription.getCustomer())
                .orElse(null);

        // If StripeCustomer not found yet, it means checkout.session.completed
        // hasn't been processed. Throw to retry later.
        if (stripeCustomer == null) {
            throw new IllegalStateException(
                    "StripeCustomer not found for customerId=" + subscription.getCustomer()
                    + ". checkout.session.completed may not have been processed yet. Will retry.");
        }

        BillingSubscription row = subscriptionRepository
                .findById(subscription.getId())
                .orElseGet(BillingSubscription::new);

        if (row.getLastStripeEventCreated() != null && event.getCreated() < row.getLastStripeEventCreated()) {
            return; // out-of-order old event
        }

        row.setStripeSubscriptionId(subscription.getId());
        row.setStripeCustomerId(subscription.getCustomer());
        row.setUserId(stripeCustomer.getUserId());
        row.setStatus(subscription.getStatus());
        row.setCancelAtPeriodEnd(Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()));
        row.setLastStripeEventCreated(event.getCreated());
        row.setUpdatedAt(Instant.now());
        subscriptionRepository.save(row);

        boolean premium = Set.of("active", "trialing").contains(subscription.getStatus());

        if (premium) {
            log.info("Updating premium status for userId: {}", stripeCustomer.getUserId());
            subscriptionGrpcClient.updatePremiumUser(stripeCustomer.getUserId());
        }
    }

}
