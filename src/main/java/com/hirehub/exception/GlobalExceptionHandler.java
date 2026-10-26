package com.hirehub.exception;

import com.hirehub.dto.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates exceptions into the standard {@link ErrorResponse} shape — see API.md.
 *
 * <p><b>History:</b> built incrementally, one handler per feature that first needed one,
 * starting with the minimum needed to ship authentication safely in Phase 4B. Phase 12 is
 * the first deliberate audit pass across the whole class rather than another incremental
 * addition — see the four handlers below added specifically because that audit found real
 * gaps, not because a new feature demanded them.</p>
 *
 * <p>Every branch returns a message safe to show a client. Never a stack trace, never a
 * raw exception message from a lower layer (the JDBC driver, Hibernate) — see the generic
 * handler at the bottom, which logs the real detail server-side and returns a fixed string.</p>
 *
 * <p><b>Handler ordering matters here in a way that is easy to miss.</b> Spring resolves
 * {@code @ExceptionHandler} methods (via {@code ExceptionHandlerExceptionResolver}) before
 * falling back to Spring MVC's own default handling (via
 * {@code DefaultHandlerExceptionResolver}) for exceptions like
 * {@code HttpRequestMethodNotSupportedException}. That means the generic
 * {@code handleUnexpected} catch-all below — {@code @ExceptionHandler(Exception.class)} —
 * was silently intercepting those Spring MVC-internal exceptions and turning a correct 405
 * into an incorrect 500, for every endpoint in the app, since the day this class first added
 * a catch-all in Phase 4B. The Phase 12 audit is what caught it; the fix is simply to add a
 * specific handler for each such exception ahead of the generic one, so Spring never reaches
 * the point of needing its own fallback.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(DuplicateResourceException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidStateException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage(), request);
    }

    /**
     * {@code Job} and {@code Application} carry a {@code @Version} column (Phase 2B). If two
     * requests load the same row and both try to save, the second one's
     * {@code UPDATE ... WHERE version = ?} matches zero rows and Hibernate throws this.
     * ARCHITECTURE.md §6 promised a 409 for exactly this case — most likely on an
     * application, where two recruiters can move the same candidate to different stages at
     * once. The loser is told to reload and retry rather than silently overwriting the winner.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                                HttpServletRequest request) {
        log.warn("Optimistic lock conflict at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "This resource was modified by someone else. Reload it and try again.", request);
    }

    /**
     * The concurrency backstop for every service-level duplicate check in the app (see
     * {@link DuplicateResourceException}'s Javadoc): two racing requests can both pass a
     * service check before either writes, and the loser's insert fails here instead, at the
     * database's UNIQUE constraint. Deliberately generic — this handler cannot know which
     * constraint fired without parsing a driver-specific message, and guessing wrong would
     * be worse than a slightly vaguer message.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                        HttpServletRequest request) {
        log.warn("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE",
                "The request conflicts with an existing resource", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                            HttpServletRequest request) {
        // LinkedHashMap: field errors are reported to the client in the same order the
        // DTO declares them, which makes a form UI's first-error-wins display deterministic.
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(
                error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ErrorResponse body = ErrorResponse.ofValidation(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
                "Request validation failed", request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Covers malformed JSON and, notably, a value that does not match a target enum - e.g.
     * {@code "role": "ADMIN"} against {@link com.hirehub.dto.auth.RegistrableRole}, which
     * has no such constant. Bean Validation never runs in that case because Jackson fails
     * before the object is even constructed, so without this handler the request would fall
     * through to the generic 500 handler instead of a 400 - a good example of why "handle
     * validation failures" and "handle unreadable bodies" have to be two different handlers.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing, malformed, or contains an invalid value", request);
    }

    /**
     * Covers both wrong credentials and an unknown email: {@code DaoAuthenticationProvider}
     * translates "no such user" into this same exception by default specifically to stop a
     * caller from telling registered emails apart from unregistered ones by the error they
     * get back. The response message preserves that: never "no such user", always this.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password", request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledAccount(DisabledException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED",
                "This account has been disabled. Contact an administrator.", request);
    }

    /** Fallback for any other authentication failure Spring Security raises. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication failed", request);
    }

    /**
     * Catches both {@code @PreAuthorize} role failures (thrown by the method-security
     * interceptor, routed here via {@code ExceptionTranslationFilter} since request handling
     * has already reached the {@code DispatcherServlet} by the time a controller method's
     * annotation fails) and every service-thrown ownership violation since Phase 5+6
     * ({@code CompanyService}, {@code JobService}, {@code ApplicationService} all throw this
     * directly) — deliberately the same exception type for both, so a role failure and an
     * ownership failure are indistinguishable to a client.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action", request);
    }

    /**
     * A {@code @PathVariable Long id} that can't parse as a number ({@code GET /api/jobs/abc})
     * throws this during argument binding, before any controller code runs. Without a
     * specific handler it would fall through to the generic 500 - the client sent a plainly
     * malformed request, not something the server failed at.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                             HttpServletRequest request) {
        String message = "Parameter '" + ex.getName() + "' has an invalid value";
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", message, request);
    }

    /** Defensive: no current endpoint has a required query parameter without a default, but a future one might. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Required parameter '" + ex.getParameterName() + "' is missing", request);
    }

    /**
     * Bean Validation on a method parameter directly (e.g. {@code @Min} on a
     * {@code @RequestParam}) throws this instead of {@link MethodArgumentNotValidException},
     * which only covers {@code @Valid @RequestBody}. No controller uses that style yet, but
     * the two failure modes look identical to a client and deserve the identical response.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                     HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            fieldErrors.putIfAbsent(field, violation.getMessage());
        });
        ErrorResponse body = ErrorResponse.ofValidation(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
                "Request validation failed", request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * A request to an existing path with an HTTP method that path doesn't support - e.g.
     * {@code PATCH /api/companies/1}, which only maps {@code GET} and {@code PUT}. See this
     * class's Javadoc for why this needs its own handler at all: without it, the generic
     * catch-all below silently turns the correct 405 into an incorrect 500.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                    HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                ex.getMethod() + " is not supported for this endpoint", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE",
                "Uploaded resume file exceeds the maximum permitted size of 10MB", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // The only place in the app allowed to log a full exception at ERROR with its
        // stack trace - and the only response body allowed to hide one entirely.
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message,
                                                 HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(status.value(), error, message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
