package com.tinder.profiles.domain.profile;

/**
 * Raised when a domain invariant or business rule is violated. Framework-free
 * on purpose: the domain layer must not depend on Spring/HTTP. The API layer is
 * responsible for translating this into a transport-specific response
 * (e.g. HTTP 400).
 */
public class DomainValidationException extends RuntimeException {
    public DomainValidationException(String message) {
        super(message);
    }
}
