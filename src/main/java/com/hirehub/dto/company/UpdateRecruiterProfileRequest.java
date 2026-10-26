package com.hirehub.dto.company;

import jakarta.validation.constraints.Size;

public record UpdateRecruiterProfileRequest(

        @Size(max = 100, message = "Designation must be at most 100 characters")
        String designation
) {
}
