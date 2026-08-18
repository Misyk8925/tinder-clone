package com.tinder.deckread.messaging;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class DeckMaterializationRequestDeserializer
        extends ObjectMapperDeserializer<DeckMaterializationRequest> {
    public DeckMaterializationRequestDeserializer() {
        super(DeckMaterializationRequest.class);
    }
}
