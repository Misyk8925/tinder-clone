package com.tinder.deckread.messaging;

import com.tinder.contracts.event.v1.DeckBuiltEventV1;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class DeckBuiltEventV1Deserializer extends ObjectMapperDeserializer<DeckBuiltEventV1> {
    public DeckBuiltEventV1Deserializer() {
        super(DeckBuiltEventV1.class);
    }
}
