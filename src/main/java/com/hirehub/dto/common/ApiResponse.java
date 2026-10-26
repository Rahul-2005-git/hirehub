package com.hirehub.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard envelope for successful API responses.
 *
 * <p>WHY: a single, predictable response shape means clients never have to guess whether
 * they received a raw object, a list, or an error. Errors use a separate, richer shape
 * ({@code ErrorResponse}, added in Phase 12) because they carry a timestamp, an error code
 * and the request path.</p>
 *
 * <p>Implemented as a {@code record}: immutable, no boilerplate, no Lombok needed.
 * {@code data} is nullable (e.g. a successful delete), so null fields are omitted from JSON.</p>
 *
 * @param success always {@code true} for this type; kept so clients can branch on one field
 * @param message short human-readable outcome
 * @param data    the payload, or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> of(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> of(String message) {
        return new ApiResponse<>(true, message, null);
    }
}
