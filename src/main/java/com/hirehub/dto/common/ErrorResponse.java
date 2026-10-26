package com.hirehub.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standard shape for every error response in the API — see API.md's error-envelope
 * section. A separate type from {@link ApiResponse} rather than a variant of it: an error
 * needs a timestamp, an HTTP-independent error code and the request path, none of which a
 * success response carries.
 *
 * <p>This is deliberately introduced here in Phase 4B, ahead of the full custom-exception
 * catalogue planned for Phase 12 ({@code ResourceNotFoundException}, {@code JobClosedException},
 * etc.) — authentication cannot ship safely without *some* structured error handling, so a
 * minimal {@code GlobalExceptionHandler} covering auth's own failure modes is built now and
 * will be extended, not replaced, in Phase 12.</p>
 *
 * @param fieldErrors present only for validation failures — field name → message
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ErrorResponse ofValidation(int status, String error, String message, String path,
                                              Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
    }
}
