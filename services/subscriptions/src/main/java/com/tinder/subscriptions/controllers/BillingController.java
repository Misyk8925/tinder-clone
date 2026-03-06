package com.tinder.subscriptions.controllers;

import com.tinder.subscriptions.stripeServices.BillingService;
import com.tinder.subscriptions.stripeServices.StripeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final StripeConfig stripeConfig;

    @PostMapping("/checkout-session")
    public String createCheckoutSession(@RequestParam String userId) throws Exception {
        log.info(stripeConfig.getSecretKey());
        return billingService.createSession(userId);
    }

    @PostMapping("/portal-session")
    public String createPortalSession(@RequestParam String userId) throws Exception {
        return billingService.createPortalSession(userId);
    }
}
