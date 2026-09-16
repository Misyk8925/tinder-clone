package com.tinder.deck.service.scoring;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assigns a viewer to an experiment variant from the viewer id alone.
 *
 * <p>The assignment is a pure function of {@code viewerId} and the experiment salt, so it
 * is never stored and never travels on a request, a deck response or a swipe event.
 * Analysis recomputes it later from the same two inputs — which is why changing the salt
 * of a running experiment destroys the replay of past assignments.
 *
 * <p>SHA-256 is used rather than {@code String.hashCode} so the split is stable across
 * JVMs, and split keys are sorted so it does not depend on configuration order.
 */
@Component
public class VariantAssigner {

    public String assign(UUID viewerId, String salt, Map<String, Integer> split, String fallback) {
        if (viewerId == null || split == null || split.isEmpty()) {
            return fallback;
        }

        List<String> names = new ArrayList<>(split.keySet());
        names.sort(String::compareTo);

        long total = 0;
        for (String name : names) {
            Integer weight = split.get(name);
            if (weight != null && weight > 0) {
                total += weight;
            }
        }
        if (total <= 0) {
            return fallback;
        }

        long bucket = Long.remainderUnsigned(hash(viewerId, salt), total);

        long cumulative = 0;
        for (String name : names) {
            Integer weight = split.get(name);
            if (weight == null || weight <= 0) {
                continue;
            }
            cumulative += weight;
            if (bucket < cumulative) {
                return name;
            }
        }
        return fallback;
    }

    private static long hash(UUID viewerId, String salt) {
        String material = viewerId + ":" + (salt == null ? "" : salt);
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every Java platform; absence is not recoverable here.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong() >>> 1;
    }
}
