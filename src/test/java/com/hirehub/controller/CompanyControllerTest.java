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
class CompanyControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String uniqueEmail() {
        return "company-test-" + UUID.randomUUID() + "@hirehub.test";
    }

    private String uniqueCompanyName() {
        return "Acme " + UUID.randomUUID();
    }

    private String recruiterToken() throws Exception {
        String payload = """
                {"email":"%s","password":"Password123","fullName":"Test Recruiter","role":"RECRUITER"}
                """.formatted(uniqueEmail());
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private String candidateToken() throws Exception {
        String payload = """
                {"email":"%s","password":"Password123","fullName":"Test Candidate","role":"CANDIDATE"}
                """.formatted(uniqueEmail());
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private long createCompany(String recruiterAuthHeader, String name) throws Exception {
        String response = mockMvc.perform(post("/api/companies").header("Authorization", recruiterAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    @Test
    @DisplayName("a recruiter can create a company and it starts PENDING")
    void createCompanySucceeds() throws Exception {
        String token = "Bearer " + recruiterToken();

        mockMvc.perform(post("/api/companies").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","website":"https://acme.example.test"}
                                """.formatted(uniqueCompanyName())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.website").value("https://acme.example.test"));
    }

    @Test
    @DisplayName("a candidate cannot create a company")
    void candidateCannotCreateCompany() throws Exception {
        String token = "Bearer " + candidateToken();

        mockMvc.perform(post("/api/companies").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(uniqueCompanyName())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("a recruiter who already belongs to a company cannot create a second one")
    void recruiterCannotCreateTwoCompanies() throws Exception {
        String token = "Bearer " + recruiterToken();
        createCompany(token, uniqueCompanyName());

        mockMvc.perform(post("/api/companies").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(uniqueCompanyName())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("two companies cannot share a name")
    void companyNameMustBeUnique() throws Exception {
        String name = uniqueCompanyName();
        createCompany("Bearer " + recruiterToken(), name);

        mockMvc.perform(post("/api/companies").header("Authorization", "Bearer " + recruiterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("a company's public profile is readable without any token")
    void getByIdIsPublic() throws Exception {
        long id = createCompany("Bearer " + recruiterToken(), uniqueCompanyName());

        mockMvc.perform(get("/api/companies/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
    }

    @Test
    @DisplayName("viewing a non-existent company returns 404")
    void getByIdMissingIsNotFound() throws Exception {
        mockMvc.perform(get("/api/companies/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("the owning recruiter can update their company, and the change persists")
    void ownerCanUpdateCompany() throws Exception {
        String token = "Bearer " + recruiterToken();
        long id = createCompany(token, uniqueCompanyName());

        mockMvc.perform(put("/api/companies/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed Co %s","industry":"Software"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("Software"));

        mockMvc.perform(get("/api/companies/" + id))
                .andExpect(jsonPath("$.data.industry").value("Software"));
    }

    @Test
    @DisplayName("a recruiter cannot edit a company they do not belong to")
    void nonOwnerCannotUpdateCompany() throws Exception {
        long id = createCompany("Bearer " + recruiterToken(), uniqueCompanyName());
        String otherRecruiterToken = "Bearer " + recruiterToken();

        mockMvc.perform(put("/api/companies/" + id).header("Authorization", otherRecruiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hostile Takeover Attempt"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("a recruiter with no company at all cannot edit any company by guessing an id")
    void recruiterWithNoCompanyCannotUpdateAnyCompany() throws Exception {
        long id = createCompany("Bearer " + recruiterToken(), uniqueCompanyName());
        String recruiterWithNoCompany = "Bearer " + recruiterToken();

        mockMvc.perform(put("/api/companies/" + id).header("Authorization", recruiterWithNoCompany)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Whatever"}
                                """))
                .andExpect(status().isForbidden());
    }
}
