package com.tinder.subscriptions.controllers;

import com.stripe.exception.StripeException;
import com.tinder.subscriptions.events.StripeWebhookIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeWebhookIngestService service;

    @PostMapping()
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) throws StripeException {
        service.ingestEvent(payload, sigHeader);
        return ResponseEntity.ok().build();
    }

}
