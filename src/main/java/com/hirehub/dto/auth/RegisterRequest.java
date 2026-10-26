package com.hirehub.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param password validated for length and basic complexity here, at the boundary — the
 *                  earliest point a bad password can be rejected, before it is ever hashed
 *                  or touches the database. Kept intentionally simple (length + one letter
 *                  + one digit): a portfolio project's registration form is not the place
 *                  to reimplement a password-strength meter, and an overly strict pattern
 *                  would only make the demo harder to exercise.
 * @param role      {@link RegistrableRole}, not {@link com.hirehub.entity.enums.RoleName} —
 *                  see that type's Javadoc for why.
 */
public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
                message = "Password must contain at least one letter and one digit")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        String fullName,

        @Pattern(regexp = "^$|^[+0-9 ()-]{7,20}$", message = "Phone number is not valid")
        String phone,

        @NotNull(message = "Role is required")
        RegistrableRole role
) {
}
