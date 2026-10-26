package com.hirehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hirehub.entity.RecruiterProfile;
import com.hirehub.entity.Role;
import com.hirehub.entity.User;
import com.hirehub.entity.enums.RoleName;
import com.hirehub.repository.CompanyRepository;
import com.hirehub.repository.RecruiterProfileRepository;
import com.hirehub.repository.RoleRepository;
import com.hirehub.repository.UserRepository;
import com.hirehub.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ApplicationControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RecruiterProfileRepository recruiterProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------------------------------------------------------------- fixtures

    private String uniqueEmail() {
        return "app-test-" + UUID.randomUUID() + "@hirehub.test";
    }

    private String registerToken(RoleName role) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123","fullName":"Test User","role":"%s"}
                                """.formatted(uniqueEmail(), role)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private String loginToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    /** A candidate whose profile has the headline and resume link required to apply. */
    private String completeCandidateToken() throws Exception {
        String token = registerToken(RoleName.CANDIDATE);
        mockMvc.perform(put("/api/candidates/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"headline":"Backend Engineer","resumeUrl":"https://files.example.test/cv.pdf"}
                        """));
        return token;
    }

    private String adminToken() throws Exception {
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        String email = uniqueEmail();
        userRepository.save(new User(email, passwordEncoder.encode("Password123"), "Test Admin", adminRole));
        return loginToken(email);
    }

    /** {recruiterToken, companyId, jobId} - an approved company with one PUBLISHED job. */
    private String[] publishedJob() throws Exception {
        String recruiterToken = registerToken(RoleName.RECRUITER);
        String companyResponse = mockMvc.perform(post("/api/companies")
                        .header("Authorization", "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"App Test Co %s"}
                                """.formatted(UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString();
        String companyId = objectMapper.readTree(companyResponse).path("data").path("id").asText();

        mockMvc.perform(patch("/api/admin/companies/" + companyId + "/approval")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decision":"APPROVED"}
                        """));

        String jobResponse = mockMvc.perform(post("/api/jobs").header("Authorization", "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Engineer %s","description":"Build.","location":"Pune",
                                 "employmentType":"FULL_TIME","experienceLevel":"MID_LEVEL"}
                                """.formatted(UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString();
        String jobId = objectMapper.readTree(jobResponse).path("data").path("id").asText();

        mockMvc.perform(patch("/api/jobs/" + jobId + "/publish").header("Authorization", "Bearer " + recruiterToken));
        return new String[]{recruiterToken, companyId, jobId};
    }

    private long apply(String candidateToken, String jobId) throws Exception {
        String response = mockMvc.perform(post("/api/jobs/" + jobId + "/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"coverLetter":"I would love to join."}
                                """))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private void setStatus(String recruiterToken, long applicationId, String status) throws Exception {
        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                .header("Authorization", "Bearer " + recruiterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"%s"}
                        """.formatted(status)));
    }

    // ---------------------------------------------------------------- applying

    @Test
    @DisplayName("applying with a complete profile creates an APPLIED application with its first history entry")
    void applySucceeds() throws Exception {
        String[] job = publishedJob();
        String candidate = completeCandidateToken();

        String response = mockMvc.perform(post("/api/jobs/" + job[2] + "/applications")
                        .header("Authorization", "Bearer " + candidate)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"coverLetter":"Hello"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.coverLetter").value("Hello"))
                .andExpect(jsonPath("$.data.resumeUrl").value("https://files.example.test/cv.pdf"))
                .andReturn().getResponse().getContentAsString();
        long applicationId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(get("/api/applications/" + applicationId + "/history")
                        .header("Authorization", "Bearer " + candidate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].toStatus").value("APPLIED"))
                .andExpect(jsonPath("$.data[0].fromStatus").doesNotExist());
    }

    @Test
    @DisplayName("a cover letter is optional")
    void applyWithoutBodySucceeds() throws Exception {
        String[] job = publishedJob();
        String candidate = completeCandidateToken();

        mockMvc.perform(post("/api/jobs/" + job[2] + "/applications")
                        .header("Authorization", "Bearer " + candidate))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a candidate with an incomplete profile cannot apply")
    void incompleteProfileCannotApply() throws Exception {
        String[] job = publishedJob();
        String freshCandidate = registerToken(RoleName.CANDIDATE);

        mockMvc.perform(post("/api/jobs/" + job[2] + "/applications")
                        .header("Authorization", "Bearer " + freshCandidate))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("applying to the same job twice is a 409")
    void duplicateApplicationIsRejected() throws Exception {
        String[] job = publishedJob();
        String candidate = completeCandidateToken();
        apply(candidate, job[2]);

        mockMvc.perform(post("/api/jobs/" + job[2] + "/applications")
                        .header("Authorization", "Bearer " + candidate))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("applying to a job that does not exist is a 404")
    void applyToMissingJobIsNotFound() throws Exception {
        mockMvc.perform(post("/api/jobs/999999999/applications")
                        .header("Authorization", "Bearer " + completeCandidateToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("applying to a DRAFT job looks identical to applying to a job that doesn't exist")
    void applyToDraftJobIsNotFound() throws Exception {
        String recruiterToken = registerToken(RoleName.RECRUITER);
        mockMvc.perform(post("/api/companies").header("Authorization", "Bearer " + recruiterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Draft Co %s"}
                        """.formatted(UUID.randomUUID())));
        String jobResponse = mockMvc.perform(post("/api/jobs").header("Authorization", "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Still Draft","description":"d","location":"Pune",
                                 "employmentType":"FULL_TIME","experienceLevel":"ENTRY_LEVEL"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String draftJobId = objectMapper.readTree(jobResponse).path("data").path("id").asText();

        mockMvc.perform(post("/api/jobs/" + draftJobId + "/applications")
                        .header("Authorization", "Bearer " + completeCandidateToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("applying to a closed job is rejected")
    void applyToClosedJobIsRejected() throws Exception {
        String[] job = publishedJob();
        mockMvc.perform(patch("/api/jobs/" + job[2] + "/close").header("Authorization", "Bearer " + job[0]));

        mockMvc.perform(post("/api/jobs/" + job[2] + "/applications")
                        .header("Authorization", "Bearer " + completeCandidateToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("a recruiter cannot apply to a job")
    void recruiterCannotApply() throws Exception {
        String[] job = publishedJob();

        mockMvc.perform(post("/api/jobs/" + job[2] + "/applications")
                        .header("Authorization", "Bearer " + registerToken(RoleName.RECRUITER)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- pipeline + history

    @Test
    @DisplayName("a recruiter can move an application through the whole pipeline, and every step is recorded in order")
    void fullPipelineIsRecordedInHistory() throws Exception {
        String[] job = publishedJob();
        String candidate = completeCandidateToken();
        long applicationId = apply(candidate, job[2]);

        for (String next : new String[]{"UNDER_REVIEW", "SHORTLISTED", "INTERVIEW", "SELECTED"}) {
            mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                            .header("Authorization", "Bearer " + job[0])
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"%s","note":"moving to %s"}
                                    """.formatted(next, next)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(next));
        }

        String historyJson = mockMvc.perform(get("/api/applications/" + applicationId + "/history")
                        .header("Authorization", "Bearer " + candidate))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode history = objectMapper.readTree(historyJson).path("data");

        assertThat(history.size()).isEqualTo(5);
        String[] expectedOrder = {"APPLIED", "UNDER_REVIEW", "SHORTLISTED", "INTERVIEW", "SELECTED"};
        for (int i = 0; i < expectedOrder.length; i++) {
            assertThat(history.get(i).path("toStatus").asText()).isEqualTo(expectedOrder[i]);
        }
        assertThat(history.get(1).path("fromStatus").asText()).isEqualTo("APPLIED");
        assertThat(history.get(1).path("note").asText()).isEqualTo("moving to UNDER_REVIEW");
        assertThat(history.get(1).path("changedByName").asText()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("skipping a pipeline stage is rejected")
    void skippingAStageIsRejected() throws Exception {
        String[] job = publishedJob();
        long applicationId = apply(completeCandidateToken(), job[2]);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", "Bearer " + job[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SHORTLISTED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("a rejected application cannot be moved again")
    void terminalStatusIsFinal() throws Exception {
        String[] job = publishedJob();
        long applicationId = apply(completeCandidateToken(), job[2]);
        setStatus(job[0], applicationId, "REJECTED");

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", "Bearer " + job[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"UNDER_REVIEW"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a recruiter cannot set WITHDRAWN - that belongs to the candidate")
    void recruiterCannotSetWithdrawn() throws Exception {
        String[] job = publishedJob();
        long applicationId = apply(completeCandidateToken(), job[2]);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", "Bearer " + job[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"WITHDRAWN"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("a candidate cannot change an application's pipeline status")
    void candidateCannotUpdatePipelineStatus() throws Exception {
        String[] job = publishedJob();
        String candidate = completeCandidateToken();
        long applicationId = apply(candidate, job[2]);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", "Bearer " + candidate)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SELECTED"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a recruiter from a different company cannot manage the application")
    void otherCompanyRecruiterCannotUpdateStatus() throws Exception {
        String[] job = publishedJob();
        long applicationId = apply(completeCandidateToken(), job[2]);
        String[] otherJob = publishedJob();

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", "Bearer " + otherJob[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"UNDER_REVIEW"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("a colleague at the same company can manage an application on the company's job")
    void colleagueCanUpdateStatus() throws Exception {
        String[] job = publishedJob();
        long applicationId = apply(completeCandidateToken(), job[2]);

        String colleagueEmail = uniqueEmail();
        Role recruiterRole = roleRepository.findByName(RoleName.RECRUITER).orElseThrow();
        User colleague = userRepository.save(
                new User(colleagueEmail, passwordEncoder.encode("Password123"), "Colleague", recruiterRole));
        RecruiterProfile profile = new RecruiterProfile(colleague);
        profile.setCompany(companyRepository.findById(Long.valueOf(job[1])).orElseThrow());
        recruiterProfileRepository.save(profile);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header("Authorization", "Bearer " + loginToken(colleagueEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"UNDER_REVIEW"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"));
    }

    // ---------------------------------------------------------------- withdraw

    @Test
    @DisplayName("a candidate can withdraw their own application, once")
    void withdrawOwnApplication() throws Exception {
        String[] job = publishedJob();
        String candidate = completeCandidateToken();
        long applicationId = apply(candidate, job[2]);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/withdraw")
                        .header("Authorization", "Bearer " + candidate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        mockMvc.perform(patch("/api/applications/" + applicationId + "/withdraw")
                        .header("Authorization", "Bearer " + candidate))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a candidate cannot withdraw someone else's application")
    void cannotWithdrawAnotherCandidatesApplication() throws Exception {
        String[] job = publishedJob();
        long applicationId = apply(completeCandidateToken(), job[2]);

        mockMvc.perform(patch("/api/applications/" + applicationId + "/withdraw")
                        .header("Authorization", "Bearer " + completeCandidateToken()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- reading

    @Test
    @DisplayName("the owning candidate and the job's recruiter can view an application; another candidate cannot")
    void viewAccessRules() throws Exception {
        String[] job = publishedJob();
        String candidate = completeCandidateToken();
        long applicationId = apply(candidate, job[2]);

        mockMvc.perform(get("/api/applications/" + applicationId).header("Authorization", "Bearer " + candidate))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/applications/" + applicationId).header("Authorization", "Bearer " + job[0]))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/applications/" + applicationId)
                        .header("Authorization", "Bearer " + completeCandidateToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/applications/" + applicationId + "/history")
                        .header("Authorization", "Bearer " + completeCandidateToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin can view any application")
    void adminCanViewAnyApplication() throws Exception {
        String[] job = publishedJob();
        long applicationId = apply(completeCandidateToken(), job[2]);

        mockMvc.perform(get("/api/applications/" + applicationId).header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("viewing a non-existent application is a 404")
    void missingApplicationIsNotFound() throws Exception {
        mockMvc.perform(get("/api/applications/999999999")
                        .header("Authorization", "Bearer " + completeCandidateToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("my applications lists only my own, and can be filtered by status")
    void myApplicationsAreScopedAndFilterable() throws Exception {
        String[] jobA = publishedJob();
        String[] jobB = publishedJob();
        String candidate = completeCandidateToken();
        long appA = apply(candidate, jobA[2]);
        apply(candidate, jobB[2]);
        setStatus(jobA[0], appA, "UNDER_REVIEW");

        mockMvc.perform(get("/api/candidates/me/applications").header("Authorization", "Bearer " + candidate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/candidates/me/applications?status=UNDER_REVIEW")
                        .header("Authorization", "Bearer " + candidate))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(appA));

        mockMvc.perform(get("/api/candidates/me/applications")
                        .header("Authorization", "Bearer " + completeCandidateToken()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("a recruiter sees the applicants for their own job, filterable by status")
    void recruiterSeesJobApplicants() throws Exception {
        String[] job = publishedJob();
        long first = apply(completeCandidateToken(), job[2]);
        apply(completeCandidateToken(), job[2]);
        setStatus(job[0], first, "UNDER_REVIEW");

        mockMvc.perform(get("/api/jobs/" + job[2] + "/applications").header("Authorization", "Bearer " + job[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/jobs/" + job[2] + "/applications?status=UNDER_REVIEW")
                        .header("Authorization", "Bearer " + job[0]))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("a recruiter from another company cannot list a job's applicants; a candidate cannot either")
    void jobApplicantListIsProtected() throws Exception {
        String[] job = publishedJob();
        String[] otherJob = publishedJob();

        mockMvc.perform(get("/api/jobs/" + job[2] + "/applications")
                        .header("Authorization", "Bearer " + otherJob[0]))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/jobs/" + job[2] + "/applications")
                        .header("Authorization", "Bearer " + completeCandidateToken()))
                .andExpect(status().isForbidden());
    }
}
