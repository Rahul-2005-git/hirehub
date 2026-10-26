package com.hirehub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the {@code bearerAuth} scheme that {@code @SecurityRequirement(name = "bearerAuth")}
 * annotations (starting with {@code AuthController.me}) refer to. Without this bean,
 * Swagger UI would still render those endpoints but with no "Authorize" affordance to
 * attach a token — the annotation would document a requirement Swagger UI couldn't help
 * satisfy. Full Swagger polish (grouping, examples, response schemas for every endpoint) is
 * Phase 14's job; this is the minimum needed for the scheme referenced today to actually work.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hireHubOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
