package com.tinder.subscriptions.controllers;

import com.tinder.subscriptions.stripeServices.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    /**
     * Creates a Stripe Checkout session for the authenticated user.
     * User ID is taken from the JWT 'sub' claim — never from the request body.
     */
    @PostMapping("/checkout-session")
    public String createCheckoutSession(@AuthenticationPrincipal Jwt jwt) throws Exception {
        String userId = jwt.getSubject();
        log.info("Creating checkout session for user {}", userId);
        return billingService.createSession(userId);
    }

    /**
     * Creates a Stripe Customer Portal session for the authenticated user.
     */
    @PostMapping("/portal-session")
    public String createPortalSession(@AuthenticationPrincipal Jwt jwt) throws Exception {
        String userId = jwt.getSubject();
        return billingService.createPortalSession(userId);
    }
}



