package com.tinder.subscriptions.stripeServices;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.param.SubscriptionListParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StripeSubscriptionGateway {

    private final StripeConfig stripeConfig;

    public List<Subscription> listByCustomer(String stripeCustomerId) throws StripeException {
        configureApiKey();
        var collection = Subscription.list(SubscriptionListParams.builder()
                .setCustomer(stripeCustomerId)
                .addExpand("data.items.data.price")
                .setLimit(10L)
                .build());
        return collection.getData() == null ? List.of() : collection.getData();
    }

    private void configureApiKey() {
        String secretKey = stripeConfig.getSecretKey();
        if (!StringUtils.hasText(secretKey) || "placeholder".equals(secretKey)) {
            throw new IllegalStateException("Stripe API key is missing. Set STRIPE_SECRET_KEY before calling billing endpoints.");
        }
        com.stripe.Stripe.apiKey = secretKey;
    }
}
