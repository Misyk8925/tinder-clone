package com.tinder.deckread.messaging;

import com.tinder.contracts.event.v1.ProfileDeckCardProjectionEvent;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class ProfileDeckCardProjectionDeserializer
        extends ObjectMapperDeserializer<ProfileDeckCardProjectionEvent> {
    public ProfileDeckCardProjectionDeserializer() {
        super(ProfileDeckCardProjectionEvent.class);
    }
}
