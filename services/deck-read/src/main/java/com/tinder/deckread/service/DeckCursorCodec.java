package com.tinder.deckread.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** Signed opaque generation/position cursor. */
@ApplicationScoped
public class DeckCursorCodec {

    @ConfigProperty(name = "deck-read.cursor-secret")
    String secret;

    public String encode(long generation, int position) {
        String payload = generation + ":" + position;
        return base64(payload.getBytes(StandardCharsets.UTF_8)) + "." + base64(sign(payload));
    }

    public Cursor decode(String encoded) {
        try {
            String[] parts = encoded.split("\\.", -1);
            if (parts.length != 2) {
                throw new InvalidCursorException();
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(sign(payload), suppliedSignature)) {
                throw new InvalidCursorException();
            }
            String[] fields = payload.split(":", -1);
            long generation = Long.parseLong(fields[0]);
            int position = Integer.parseInt(fields[1]);
            if (fields.length != 2 || generation < 1 || position < 0) {
                throw new InvalidCursorException();
            }
            return new Cursor(generation, position);
        } catch (InvalidCursorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidCursorException();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign deck cursor", e);
        }
    }

    private String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public record Cursor(long generation, int position) {
    }

    public static class InvalidCursorException extends RuntimeException {
    }
}
