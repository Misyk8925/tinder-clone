package com.tinder.subscriptions.controllers;

import com.tinder.subscriptions.events.StripeWebhookIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StripeWebhookControllerTest {

    private MockMvc mockMvc;
    private StripeWebhookIngestService ingestService;

    @BeforeEach
    void setUp() {
        ingestService = mock(StripeWebhookIngestService.class);
        StripeWebhookController controller = new StripeWebhookController(ingestService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldAcceptWebhookOnSinglePath() throws Exception {
        doNothing().when(ingestService).ingestEvent(anyString(), anyString());

        mockMvc.perform(post("/api/v1/webhook")
                        .contentType("application/json")
                        .content("{\"id\":\"evt_test\"}")
                        .header("Stripe-Signature", "sig"))
                .andExpect(status().isOk());

        verify(ingestService).ingestEvent(anyString(), anyString());
    }

    @Test
    void shouldRejectLegacyDoubleWebhookPath() throws Exception {
        mockMvc.perform(post("/api/v1/webhook/webhook")
                        .contentType("application/json")
                        .content("{\"id\":\"evt_test\"}")
                        .header("Stripe-Signature", "sig"))
                .andExpect(status().isNotFound());
    }
}

