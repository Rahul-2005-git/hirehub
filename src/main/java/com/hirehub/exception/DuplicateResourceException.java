package com.hirehub.exception;

/**
 * Thrown when a service-level uniqueness check fails — a duplicate email at registration,
 * later a duplicate company name or a duplicate application.
 *
 * <p>This is the friendly half of the two-layer duplicate-prevention pattern used
 * throughout HireHub (see DATABASE.md §6 and ARCHITECTURE.md §6): the service checks first
 * so the client gets this clear, typed error; the matching database UNIQUE constraint is
 * what actually holds under concurrency, and a race that slips past this check surfaces as
 * a {@code DataIntegrityViolationException}, mapped to the same HTTP status by
 * {@link GlobalExceptionHandler}.</p>
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
