package com.tinder.subscriptions.stripeServices;

import com.stripe.model.billingportal.Session;
import com.stripe.param.billingportal.SessionCreateParams;
import com.tinder.subscriptions.stripeCustomer.StripeCustomer;
import com.tinder.subscriptions.stripeCustomer.StripeCustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock private StripeCustomerRepository customerRepository;
    @Mock private StripeConfig stripeConfig;

    @Test
    void givenExistingCustomerWhenPortalRequestedThenCreatesBillingPortalSession() throws Exception {
        StripeCustomer customer = StripeCustomer.builder()
                .userId("user-123")
                .stripeCustomerId("cus_123")
                .build();
        when(customerRepository.findByUserId("user-123")).thenReturn(Optional.of(customer));
        when(stripeConfig.getSecretKey()).thenReturn("sk_test_fixture");

        BillingService service = new BillingService(customerRepository, stripeConfig);
        ReflectionTestUtils.setField(service, "returnUrl", "https://example.test/account");

        Session portalSession = mock(Session.class);
        when(portalSession.getUrl()).thenReturn("https://billing.stripe.test/session");
        AtomicReference<SessionCreateParams> captured = new AtomicReference<>();

        try (MockedStatic<Session> stripePortal = mockStatic(Session.class)) {
            stripePortal.when(() -> Session.create(any(SessionCreateParams.class)))
                    .thenAnswer(invocation -> {
                        captured.set(invocation.getArgument(0));
                        return portalSession;
                    });

            String url = service.createPortalSession("user-123");

            assertThat(url).isEqualTo("https://billing.stripe.test/session");
            assertThat(captured.get().getCustomer()).isEqualTo("cus_123");
            assertThat(captured.get().getReturnUrl()).isEqualTo("https://example.test/account");
        }
    }
}
