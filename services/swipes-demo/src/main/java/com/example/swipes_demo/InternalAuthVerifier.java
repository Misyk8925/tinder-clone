package com.example.swipes_demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalAuthVerifier {

    public static final String HEADER_NAME = "X-Internal-Auth";
    public static final String ATTRIBUTE_AUTHENTICATED = InternalAuthVerifier.class.getName() + ".authenticated";

    private final byte[] secretBytes;

    public InternalAuthVerifier(@Value("${swipes.internal-auth-secret:}") String secret) {
        String normalizedSecret = secret == null ? "" : secret;
        this.secretBytes = normalizedSecret.isBlank()
                ? new byte[0]
                : normalizedSecret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isValid(String candidate) {
        if (secretBytes.length == 0 || candidate == null || candidate.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
                secretBytes,
                candidate.getBytes(StandardCharsets.UTF_8)
        );
    }
}
