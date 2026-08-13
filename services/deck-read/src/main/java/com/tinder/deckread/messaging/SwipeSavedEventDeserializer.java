package com.tinder.deckread.messaging;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class SwipeSavedEventDeserializer extends ObjectMapperDeserializer<SwipeSavedEvent> {
    public SwipeSavedEventDeserializer() {
        super(SwipeSavedEvent.class);
    }
}
