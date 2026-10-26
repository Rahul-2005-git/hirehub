package com.hirehub.exception;

/**
 * Thrown when an operation is well-formed and the resource exists, but the resource's
 * current state makes the operation invalid — re-deciding an already-decided company
 * approval, for instance. Maps to 409 in {@link GlobalExceptionHandler}, alongside
 * {@link DuplicateResourceException} (409 for "already exists") and distinct from
 * {@link ResourceNotFoundException} (404 for "doesn't exist").
 *
 * <p>Kept generic rather than named per resource for now — {@code InvalidCompanyStatusException}
 * would exist to be thrown from exactly one call site. Job and application status transitions
 * (Phase 8/10/11) already have their own state machines with a {@code canTransitionTo} check
 * (see {@code JobStatus}, {@code ApplicationStatus}); if their failure modes turn out to need
 * a more specific message or status code than this generic exception gives them, the
 * project brief's own {@code JobClosedException} / {@code InvalidApplicationException} are
 * introduced then, rather than speculatively now.</p>
 */
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }
}
