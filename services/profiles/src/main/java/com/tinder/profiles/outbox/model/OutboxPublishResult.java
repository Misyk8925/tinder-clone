package com.tinder.profiles.outbox.model;

public record OutboxPublishResult(int claimed, int published, int failed, int deadLettered) {

    public static final OutboxPublishResult EMPTY = new OutboxPublishResult(0, 0, 0, 0);

    public boolean isEmpty() {
        return claimed == 0;
    }
}
