package com.hirehub.dto.company;

import com.hirehub.entity.enums.CompanyStatus;

public record CompanyResponse(
        Long id,
        String name,
        String website,
        String description,
        String industry,
        String companySize,
        String logoUrl,
        CompanyStatus status
) {
}
