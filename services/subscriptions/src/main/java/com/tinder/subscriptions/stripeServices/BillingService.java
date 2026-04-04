package com.tinder.subscriptions.stripeServices;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.tinder.subscriptions.stripeCustomer.StripeCustomer;
import com.tinder.subscriptions.stripeCustomer.StripeCustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final StripeCustomerRepository stripeCustomerRepository;
    private final StripeConfig stripeConfig;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    @Value("${stripe.return-url}")
    private String returnUrl;

    public String createSession(String userId) throws StripeException {
        ensureStripeApiKeyConfigured();
        StripeCustomer customer = getOrCreateCustomer(userId);
        String customerId = customer.getStripeCustomerId();
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .setClientReferenceId(userId)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(stripeConfig.getPriceId())
                                .setQuantity(1L)
                                .build()
                )
                .build();

        return Session.create(params).getUrl();
    }

    public String createPortalSession(String userId) throws StripeException {
        ensureStripeApiKeyConfigured();
        StripeCustomer customer = getOrCreateCustomer(userId);
        String customerId = customer.getStripeCustomerId();
        SessionCreateParams param = SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(returnUrl)
                .build();
        return Session.create(param).getUrl();
    }

    public StripeCustomer getOrCreateCustomer(String userId) {
        return stripeCustomerRepository.findByUserId(userId)
                .orElseGet(() -> createCustomer(userId));
    }

    private StripeCustomer createCustomer(String userId) {
        try {
            Customer customer = Customer.create(
                    CustomerCreateParams
                            .builder()
                            .putMetadata("userId", userId)
                            .build()
            );
            StripeCustomer stripeCustomer = StripeCustomer.builder()
                    .id(customer.getId())
                    .userId(userId)
                    .stripeCustomerId(customer.getId())
                    .build();
            return stripeCustomerRepository.save(stripeCustomer);
        } catch (StripeException e) {
            log.error("Error while creating customer", e);
            throw new RuntimeException(e);
        }
    }

    private void ensureStripeApiKeyConfigured() {
        String secretKey = stripeConfig.getSecretKey();
        if (!org.springframework.util.StringUtils.hasText(secretKey) || "placeholder".equals(secretKey)) {
            throw new IllegalStateException("Stripe API key is missing. Set STRIPE_SECRET_KEY before calling billing endpoints.");
        }
        com.stripe.Stripe.apiKey = secretKey;
    }
}
