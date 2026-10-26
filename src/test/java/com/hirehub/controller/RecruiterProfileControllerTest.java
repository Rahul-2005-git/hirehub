package com.hirehub.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RecruiterProfileControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String recruiterToken() throws Exception {
        String email = "recruiter-test-" + UUID.randomUUID() + "@hirehub.test";
        String response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123","fullName":"Test Recruiter","role":"RECRUITER"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("a fresh recruiter has a profile with no company yet")
    void freshRecruiterHasNoCompany() throws Exception {
        String token = "Bearer " + recruiterToken();

        mockMvc.perform(get("/api/recruiters/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company").doesNotExist());
    }

    @Test
    @DisplayName("updating the designation persists")
    void updateDesignationPersists() throws Exception {
        String token = "Bearer " + recruiterToken();

        mockMvc.perform(put("/api/recruiters/me").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"designation":"Senior Talent Partner"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.designation").value("Senior Talent Partner"));

        mockMvc.perform(get("/api/recruiters/me").header("Authorization", token))
                .andExpect(jsonPath("$.data.designation").value("Senior Talent Partner"));
    }

    @Test
    @DisplayName("creating a company attaches it to the recruiter's own profile")
    void creatingCompanyAttachesToProfile() throws Exception {
        String token = "Bearer " + recruiterToken();
        String companyName = "Recruiter Owned Co " + UUID.randomUUID();

        mockMvc.perform(post("/api/companies").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s"}
                        """.formatted(companyName)));

        mockMvc.perform(get("/api/recruiters/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.name").value(companyName))
                .andExpect(jsonPath("$.data.company.status").value("PENDING"));
    }
}
