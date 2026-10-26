package com.hirehub.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hirehub.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 12's audit pass found gaps in {@link GlobalExceptionHandler} that no earlier phase's
 * feature-driven testing had exercised — each test here corresponds to exactly one of them.
 * {@link #wrongHttpMethodReturns405NotInternalServerError} is the one that mattered most: it
 * proves a real, previously-shipped bug (the generic catch-all silently turning a correct
 * 405 into an incorrect 500) is actually fixed, not just plausible in theory.
 */
@AutoConfigureMockMvc
class GlobalExceptionHandlerAuditTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String anyAuthenticatedToken() throws Exception {
        String email = "audit-test-" + UUID.randomUUID() + "@hirehub.test";
        String response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123","fullName":"Audit Test","role":"CANDIDATE"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("a path variable that can't parse as the expected type is a 400, not a 500")
    void invalidPathVariableTypeIsBadRequest() throws Exception {
        // GET /api/jobs/{id} is public, so this needs no token at all to reach the handler.
        mockMvc.perform(get("/api/jobs/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("the wrong HTTP method for an existing path returns 405, not the generic 500 it used to")
    void wrongHttpMethodReturns405NotInternalServerError() throws Exception {
        // /api/companies/{id} maps only GET and PUT - PATCH is a path that exists with a
        // method that doesn't. A token is required here specifically so the request clears
        // Spring Security's coarse authenticated() check and actually reaches Spring MVC's
        // handler-method resolution, which is where HttpRequestMethodNotSupportedException
        // is thrown - a 401 from the security layer would never exercise this handler at all.
        String token = anyAuthenticatedToken();

        mockMvc.perform(patch("/api/companies/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("a malformed JSON body still returns 400 via the unreadable-body handler, unaffected by the new handlers")
    void malformedJsonStillReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }
}
