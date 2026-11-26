package com.tinder.deckread.messaging;

import com.tinder.contracts.event.v1.SwipeRevokedEventV1;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class SwipeRevokedEventV1Deserializer extends ObjectMapperDeserializer<SwipeRevokedEventV1> {
    public SwipeRevokedEventV1Deserializer() {
        super(SwipeRevokedEventV1.class);
    }
}
