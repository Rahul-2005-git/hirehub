package com.hirehub.exception;

/**
 * Thrown when a request references a resource by id (or another unique key) and nothing
 * matches — a candidate profile, later a job or an application. Maps to 404 in
 * {@link GlobalExceptionHandler}.
 *
 * <p>The second custom exception added to the project, after
 * {@link DuplicateResourceException} in Phase 4B. Both are added as the feature that first
 * needs them is built, rather than declaring the full Phase 12 catalogue
 * ({@code JobClosedException}, {@code InvalidApplicationException}, ...) up front before
 * anything throws them.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
