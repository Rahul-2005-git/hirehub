package com.hirehub.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Deliberately has no password-strength validation. WHY: at login, the password is
 * whatever the account was actually registered with — validating it against today's rules
 * here would reject a correct legacy password if the rules ever changed. Bean Validation's
 * job at login is only "was something submitted", not "is it a good password".
 */
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {
}
