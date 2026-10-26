package com.hirehub;

import com.hirehub.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test.
 *
 * <p>It is not a trivial getter test: it fails if any bean definition is broken, if a
 * required property is missing, if Flyway cannot migrate, if Hibernate's schema validation
 * disagrees with the migrations, or if the JSON contract of {@code /api/health} changes.</p>
 */
@AutoConfigureMockMvc
class HireHubApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("health endpoint returns the standard API envelope with status UP")
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.application").value("HireHub"));
    }
}
