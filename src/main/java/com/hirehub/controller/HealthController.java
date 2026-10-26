package com.hirehub.controller;

import com.hirehub.dto.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Minimal public endpoint used to verify that the application is up and that the
 * {@link ApiResponse} envelope and JSON serialization are wired correctly.
 *
 * <p>Spring Actuator already exposes {@code /actuator/health} for infrastructure probes;
 * this endpoint is the application-level equivalent that returns build metadata in the
 * project's own response format.</p>
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Service liveness and build information")
public class HealthController {

    private final String applicationName;
    private final String applicationVersion;

    // Constructor injection everywhere - no field injection. It keeps dependencies
    // explicit, allows final fields, and makes the class testable without Spring.
    public HealthController(@Value("${spring.application.name:HireHub}") String applicationName,
                            @Value("${hirehub.version:0.1.0-SNAPSHOT}") String applicationVersion) {
        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
    }

    @GetMapping
    @Operation(summary = "Service health", description = "Returns UP when the API is serving requests.")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.of("Service is healthy", Map.of(
                "status", "UP",
                "application", applicationName,
                "version", applicationVersion
        ));
    }
}
