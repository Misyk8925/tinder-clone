package com.tinder.subscriptions.stripeServices;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConfigurationProperties(prefix = "stripe")
@Getter
@Setter
@Slf4j
public class StripeConfig {

    private String secretKey;
    private String webhookSecret;
    private String priceId;

    @PostConstruct
    void initStripe() {
        if (!StringUtils.hasText(secretKey) || "placeholder".equals(secretKey)) {
            log.warn("Stripe secret key is not configured. Set STRIPE_SECRET_KEY to enable Stripe API calls.");
            return;
        }
        Stripe.apiKey = secretKey;
    }
}
