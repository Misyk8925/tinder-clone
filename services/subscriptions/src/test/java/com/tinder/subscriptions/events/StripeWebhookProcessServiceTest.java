package com.tinder.subscriptions.events;

import com.stripe.model.Price;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.model.checkout.Session;
import com.tinder.subscriptions.grpc.SubscriptionGrpcClient;
import com.tinder.subscriptions.stripeCustomer.StripeCustomer;
import com.tinder.subscriptions.stripeCustomer.StripeCustomerRepository;
import com.tinder.subscriptions.subscription.BillingSubscription;
import com.tinder.subscriptions.subscription.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookProcessServiceTest {

    @Mock private StripeEventInboxRepository inboxRepository;
    @Mock private StripeCustomerRepository customerRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionGrpcClient grpcClient;
    @Mock private TransactionTemplate transactionTemplate;

    private StripeWebhookProcessService service;

    @BeforeEach
    void setUp() {
        service = new StripeWebhookProcessService(
                inboxRepository, customerRepository, subscriptionRepository, grpcClient, transactionTemplate);
        StripeCustomer customer = new StripeCustomer();
        customer.setUserId("user-123");
        customer.setStripeCustomerId("cus_123");
        lenient().when(customerRepository.findByStripeCustomerId("cus_123")).thenReturn(Optional.of(customer));
    }

    @Test
    void givenActiveSubscriptionWhenSynchronizedThenUsesStripePaidThroughTime() {
        Instant paidThrough = Instant.parse("2030-02-03T04:05:06Z");
        Subscription subscription = subscription("active", paidThrough, "price_premium");
        when(subscriptionRepository.findById("sub_123")).thenReturn(Optional.empty());

        service.synchronizeSubscription(subscription, 100L);

        verify(grpcClient).activatePremiumUntil("user-123", paidThrough);
        verify(grpcClient, never()).revokePremium("user-123");
        ArgumentCaptor<BillingSubscription> saved = ArgumentCaptor.forClass(BillingSubscription.class);
        verify(subscriptionRepository).save(saved.capture());
        assertThat(saved.getValue().getCurrentPeriodEnd()).isEqualTo(paidThrough);
        assertThat(saved.getValue().getPriceId()).isEqualTo("price_premium");
    }

    @Test
    void givenCanceledSubscriptionWhenSynchronizedThenRevokesPremium() {
        Subscription subscription = subscription("canceled", null, null);
        when(subscriptionRepository.findById("sub_123")).thenReturn(Optional.empty());

        service.synchronizeSubscription(subscription, 101L);

        verify(grpcClient).revokePremium("user-123");
        verify(grpcClient, never()).activatePremiumUntil(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void givenCompletedCheckoutWhenSynchronizedThenOnlyMapsCustomer() {
        Session session = new Session();
        session.setClientReferenceId("user-123");
        session.setCustomer("cus_123");

        service.synchronizeCheckoutCustomer(session, true);

        ArgumentCaptor<StripeCustomer> saved = ArgumentCaptor.forClass(StripeCustomer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo("user-123");
        assertThat(saved.getValue().getLivemode()).isTrue();
        verify(grpcClient, never()).activatePremiumUntil(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        verify(grpcClient, never()).revokePremium(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void givenOlderEventWhenSynchronizedThenDoesNotOverwriteOrPropagate() {
        BillingSubscription existing = new BillingSubscription();
        existing.setLastStripeEventCreated(200L);
        when(subscriptionRepository.findById("sub_123")).thenReturn(Optional.of(existing));

        service.synchronizeSubscription(subscription("active", Instant.parse("2030-02-03T04:05:06Z"), null), 199L);

        verify(subscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(grpcClient, never()).activatePremiumUntil(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        verify(grpcClient, never()).revokePremium(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void givenProcessableRowsWhenClaimedThenMarksThemProcessingWithLease() {
        StripeEventInbox row = StripeEventInbox.builder()
                .id("evt_123")
                .status(StripeEventInbox.Status.PENDING)
                .build();
        when(transactionTemplate.execute(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(mock(TransactionStatus.class));
                });
        when(inboxRepository.claimProcessableBatch(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(row));
        when(inboxRepository.saveAll(List.of(row))).thenReturn(List.of(row));

        List<StripeEventInbox> claimed = service.claimBatch();

        assertThat(claimed).containsExactly(row);
        assertThat(row.getStatus()).isEqualTo(StripeEventInbox.Status.PROCESSING);
        assertThat(row.getNextRetryAt()).isAfter(Instant.now().plusSeconds(500));
    }

    @SuppressWarnings("unchecked")
    @Test
    void givenTenthFailureWhenProcessedThenMovesEventToFailed() {
        StripeEventInbox row = StripeEventInbox.builder()
                .id("evt_failed")
                .payloadJson("{}")
                .status(StripeEventInbox.Status.PROCESSING)
                .attempts(9)
                .build();
        when(inboxRepository.findById("evt_failed")).thenReturn(Optional.of(row));
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(org.mockito.ArgumentMatchers.any());

        service.processSingleEvent(row);

        assertThat(row.getAttempts()).isEqualTo(10);
        assertThat(row.getStatus()).isEqualTo(StripeEventInbox.Status.FAILED);
        assertThat(row.getNextRetryAt()).isNull();
        verify(inboxRepository).save(row);
    }

    private Subscription subscription(String status, Instant paidThrough, String priceId) {
        Subscription subscription = new Subscription();
        subscription.setId("sub_123");
        subscription.setCustomer("cus_123");
        subscription.setStatus(status);
        subscription.setCancelAtPeriodEnd(false);

        if (paidThrough != null || priceId != null) {
            SubscriptionItem item = new SubscriptionItem();
            if (paidThrough != null) {
                item.setCurrentPeriodEnd(paidThrough.getEpochSecond());
            }
            if (priceId != null) {
                Price price = new Price();
                price.setId(priceId);
                item.setPrice(price);
            }
            SubscriptionItemCollection items = new SubscriptionItemCollection();
            items.setData(List.of(item));
            subscription.setItems(items);
        }
        return subscription;
    }
}
