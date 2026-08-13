package com.tinder.deckread.messaging;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class MatchCreatedEventDeserializer extends ObjectMapperDeserializer<MatchCreatedEvent> {
    public MatchCreatedEventDeserializer() {
        super(MatchCreatedEvent.class);
    }
}
