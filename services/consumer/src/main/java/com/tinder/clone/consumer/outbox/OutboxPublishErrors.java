package com.tinder.clone.consumer.outbox;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class OutboxPublishErrors {

    private OutboxPublishErrors() {
    }

    static String summarize(Throwable error, int maxLength) {
        String message = chain(error);
        if (message.isBlank()) {
            return "Unknown error";
        }
        int limit = Math.max(64, maxLength);
        return message.length() > limit ? message.substring(0, limit) : message;
    }

    static String chain(Throwable error) {
        if (error == null) {
            return "";
        }
        StringBuilder message = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        while (current != null && seen.add(current)) {
            if (message.length() > 0) {
                message.append(" | ");
            }
            String part = current.getMessage();
            message.append(part == null || part.isBlank() ? current.getClass().getSimpleName() : part);
            current = current.getCause();
        }
        return message.toString();
    }
}
