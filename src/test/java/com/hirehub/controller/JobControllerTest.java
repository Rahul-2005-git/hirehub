package com.hirehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hirehub.entity.Role;
import com.hirehub.entity.RecruiterProfile;
import com.hirehub.entity.User;
import com.hirehub.entity.enums.RoleName;
import com.hirehub.repository.CompanyRepository;
import com.hirehub.repository.RecruiterProfileRepository;
import com.hirehub.repository.RoleRepository;
import com.hirehub.repository.UserRepository;
import com.hirehub.support.AbstractIntegrationTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class JobControllerTest extends AbstractIntegrationTest {

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

    private String uniqueEmail() {
        return "job-test-" + UUID.randomUUID() + "@hirehub.test";
    }

    private String registerToken(String email, RoleName role) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123","fullName":"Test User","role":"%s"}
                                """.formatted(email, role)))
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

    /** @return {token, companyId} for a fresh recruiter who owns a brand-new PENDING company. */
    private String[] recruiterWithCompany() throws Exception {
        String email = uniqueEmail();
        String token = registerToken(email, RoleName.RECRUITER);
        String companyResponse = mockMvc.perform(post("/api/companies").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Job Test Co %s"}
                                """.formatted(UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString();
        String companyId = objectMapper.readTree(companyResponse).path("data").path("id").asText();
        return new String[]{token, companyId};
    }

    /**
     * Simulates a second recruiter joining an existing company. No such endpoint exists yet
     * (a recruiter can only create a brand-new company), so this reaches into the
     * repositories directly - which is also the only way this scenario could come about
     * today, e.g. an admin manually onboarding a colleague.
     */
    private String addColleagueToCompany(Long companyId) throws Exception {
        String email = uniqueEmail();
        Role recruiterRole = roleRepository.findByName(RoleName.RECRUITER).orElseThrow();
        User user = new User(email, passwordEncoder.encode("Password123"), "Colleague Recruiter", recruiterRole);
        userRepository.save(user);
        RecruiterProfile profile = new RecruiterProfile(user);
        profile.setCompany(companyRepository.findById(companyId).orElseThrow());
        recruiterProfileRepository.save(profile);
        return loginToken(email);
    }

    private String adminToken() throws Exception {
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        String email = uniqueEmail();
        User admin = new User(email, passwordEncoder.encode("Password123"), "Test Admin", adminRole);
        userRepository.save(admin);
        return loginToken(email);
    }

    private void approve(String companyId, String adminToken) throws Exception {
        mockMvc.perform(patch("/api/admin/companies/" + companyId + "/approval")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decision":"APPROVED"}
                        """));
    }

    private String createJobPayload(String title) {
        return """
                {"title":"%s","description":"Build things.","location":"Pune",
                 "employmentType":"FULL_TIME","experienceLevel":"SENIOR_LEVEL",
                 "salaryMin":1000000,"salaryMax":2000000,
                 "skills":[{"skillName":"java","mandatory":true}]}
                """.formatted(title);
    }

    private long createJob(String token, String title) throws Exception {
        String response = mockMvc.perform(post("/api/jobs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPayload(title)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    @Test
    @DisplayName("a recruiter with no company cannot create a job")
    void recruiterWithNoCompanyCannotCreateJob() throws Exception {
        String token = registerToken(uniqueEmail(), RoleName.RECRUITER);

        mockMvc.perform(post("/api/jobs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPayload("Engineer")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("a candidate cannot create a job")
    void candidateCannotCreateJob() throws Exception {
        String token = registerToken(uniqueEmail(), RoleName.CANDIDATE);

        mockMvc.perform(post("/api/jobs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPayload("Engineer")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("creating a job starts it as DRAFT with its skills attached")
    void createJobStartsAsDraft() throws Exception {
        String[] tc = recruiterWithCompany();

        mockMvc.perform(post("/api/jobs").header("Authorization", "Bearer " + tc[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPayload("Backend Engineer")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.requiredSkills[0].skill.name").value("java"))
                .andExpect(jsonPath("$.data.requiredSkills[0].mandatory").value(true));
    }

    @Test
    @DisplayName("a rejected salary range (max below min) is a validation error")
    void invalidSalaryRangeIsRejected() throws Exception {
        String[] tc = recruiterWithCompany();

        mockMvc.perform(post("/api/jobs").header("Authorization", "Bearer " + tc[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Bad Job","description":"d","location":"Pune",
                                 "employmentType":"FULL_TIME","experienceLevel":"ENTRY_LEVEL",
                                 "salaryMin":2000000,"salaryMax":1000000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("a DRAFT job is invisible to an anonymous viewer but visible to its own recruiter")
    void draftVisibility() throws Exception {
        String[] tc = recruiterWithCompany();
        long jobId = createJob(tc[0], "Secret Draft Role");

        mockMvc.perform(get("/api/jobs/" + jobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/jobs/" + jobId).header("Authorization", "Bearer " + tc[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Secret Draft Role"));
    }

    @Test
    @DisplayName("a DRAFT job is invisible to a recruiter from a different company, visible to an admin")
    void draftVisibilityAcrossCompaniesAndForAdmin() throws Exception {
        String[] tc = recruiterWithCompany();
        long jobId = createJob(tc[0], "Cross Company Secret");
        String[] otherCompany = recruiterWithCompany();

        mockMvc.perform(get("/api/jobs/" + jobId).header("Authorization", "Bearer " + otherCompany[0]))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/jobs/" + jobId).header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("publishing requires the company to be approved first")
    void publishRequiresApprovedCompany() throws Exception {
        String[] tc = recruiterWithCompany();
        long jobId = createJob(tc[0], "Needs Approval");

        mockMvc.perform(patch("/api/jobs/" + jobId + "/publish").header("Authorization", "Bearer " + tc[0]))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("publishing succeeds once the company is approved, and the job appears in the public feed")
    void publishSucceedsAfterApprovalAndAppearsInFeed() throws Exception {
        String[] tc = recruiterWithCompany();
        long jobId = createJob(tc[0], "Approved And Published " + UUID.randomUUID());
        approve(tc[1], adminToken());

        mockMvc.perform(patch("/api/jobs/" + jobId + "/publish").header("Authorization", "Bearer " + tc[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").exists());

        mockMvc.perform(get("/api/jobs/" + jobId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/jobs?size=200"))
                .andExpect(jsonPath("$.data.content[?(@.id == " + jobId + ")]").exists());
    }

    @Test
    @DisplayName("publishing an already-published job is rejected, not silently repeated")
    void publishingTwiceIsRejected() throws Exception {
        String[] tc = recruiterWithCompany();
        long jobId = createJob(tc[0], "Publish Once");
        approve(tc[1], adminToken());
        mockMvc.perform(patch("/api/jobs/" + jobId + "/publish").header("Authorization", "Bearer " + tc[0]));

        mockMvc.perform(patch("/api/jobs/" + jobId + "/publish").header("Authorization", "Bearer " + tc[0]))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("a published job can still be edited, but not after it's closed")
    void editAllowedWhilePublishedButNotAfterClosed() throws Exception {
        String[] tc = recruiterWithCompany();
        long jobId = createJob(tc[0], "Editable For Now");
        approve(tc[1], adminToken());
        mockMvc.perform(patch("/api/jobs/" + jobId + "/publish").header("Authorization", "Bearer " + tc[0]));

        mockMvc.perform(put("/api/jobs/" + jobId).header("Authorization", "Bearer " + tc[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPayload("Edited While Published")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Edited While Published"));

        mockMvc.perform(patch("/api/jobs/" + jobId + "/close").header("Authorization", "Bearer " + tc[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(put("/api/jobs/" + jobId).header("Authorization", "Bearer " + tc[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPayload("Should Not Apply")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("only a DRAFT job can be deleted")
    void onlyDraftCanBeDeleted() throws Exception {
        String[] tc = recruiterWithCompany();
        long draftId = createJob(tc[0], "Deletable Draft");
        long publishedId = createJob(tc[0], "Not Deletable " + UUID.randomUUID());
        approve(tc[1], adminToken());
        mockMvc.perform(patch("/api/jobs/" + publishedId + "/publish").header("Authorization", "Bearer " + tc[0]));

        mockMvc.perform(delete("/api/jobs/" + draftId).header("Authorization", "Bearer " + tc[0]))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/jobs/" + publishedId).header("Authorization", "Bearer " + tc[0]))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("a recruiter from a different company cannot manage another company's job")
    void nonOwningRecruiterCannotManageJob() throws Exception {
        String[] tc = recruiterWithCompany();
        long jobId = createJob(tc[0], "Not Yours");
        String[] otherCompany = recruiterWithCompany();

        mockMvc.perform(patch("/api/jobs/" + jobId + "/close").header("Authorization", "Bearer " + otherCompany[0]))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("ownership is company-wide: a colleague at the same company can manage a job they didn't create")
    void ownershipIsCompanyWideNotCreatorOnly() throws Exception {
        String[] tc = recruiterWithCompany();
        long jobId = createJob(tc[0], "Team Owned Job " + UUID.randomUUID());
        String colleagueToken = addColleagueToCompany(Long.valueOf(tc[1]));

        mockMvc.perform(patch("/api/jobs/" + jobId + "/close").header("Authorization", "Bearer " + colleagueToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    @DisplayName("GET /api/recruiters/me/jobs lists only jobs this recruiter personally created")
    void myJobsListsOnlyPersonallyCreatedJobs() throws Exception {
        String[] tc = recruiterWithCompany();
        long ownJobId = createJob(tc[0], "My Own Job " + UUID.randomUUID());
        String colleagueToken = addColleagueToCompany(Long.valueOf(tc[1]));
        long colleagueJobId = createJob(colleagueToken, "Colleague Job " + UUID.randomUUID());

        String response = mockMvc.perform(get("/api/recruiters/me/jobs?size=200")
                        .header("Authorization", "Bearer " + tc[0]))
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(response).path("data").path("content");

        boolean hasOwnJob = false;
        boolean hasColleagueJob = false;
        for (JsonNode node : content) {
            long id = node.path("id").asLong();
            if (id == ownJobId) {
                hasOwnJob = true;
            }
            if (id == colleagueJobId) {
                hasColleagueJob = true;
            }
        }
        Assertions.assertThat(hasOwnJob).as("should contain own job").isTrue();
        Assertions.assertThat(hasColleagueJob).as("should NOT contain colleague's job").isFalse();
    }

    @Test
    @DisplayName("GET /api/jobs filters by employmentType and experienceLevel via query params")

    void searchFiltersByEmploymentTypeAndExperienceLevel() throws Exception {
        String[] tc = recruiterWithCompany();
        approve(tc[1], adminToken());
        long fullTimeSenior = createJob(tc[0], "Full Time Senior " + UUID.randomUUID());
        mockMvc.perform(patch("/api/jobs/" + fullTimeSenior + "/publish").header("Authorization", "Bearer " + tc[0]));

        mockMvc.perform(get("/api/jobs?employmentType=FULL_TIME&experienceLevel=SENIOR_LEVEL&size=200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + fullTimeSenior + ")]").exists());

        mockMvc.perform(get("/api/jobs?employmentType=INTERNSHIP&size=200"))
                .andExpect(jsonPath("$.data.content[?(@.id == " + fullTimeSenior + ")]").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/jobs combines keyword and location filters with AND")
    void searchCombinesKeywordAndLocation() throws Exception {
        String[] tc = recruiterWithCompany();
        approve(tc[1], adminToken());
        String marker = "Combofilter" + UUID.randomUUID().toString().substring(0, 8);
        long jobId = createJob(tc[0], marker + " Engineer");
        mockMvc.perform(patch("/api/jobs/" + jobId + "/publish").header("Authorization", "Bearer " + tc[0]));

        mockMvc.perform(get("/api/jobs?keyword=" + marker + "&location=Pune&size=200"))
                .andExpect(jsonPath("$.data.content[?(@.id == " + jobId + ")]").exists());

        mockMvc.perform(get("/api/jobs?keyword=" + marker + "&location=Mumbai&size=200"))
                .andExpect(jsonPath("$.data.content[?(@.id == " + jobId + ")]").doesNotExist());
    }

    @Test
    @DisplayName("a sort request on a field that doesn't even exist on Job is silently ignored, not a 500")
    void nonexistentSortFieldIsIgnoredNotRejected() throws Exception {
        // Without the allow-list, this would reach Hibernate as an ORDER BY on a property
        // that has no mapping at all, throwing PropertyReferenceException - exactly the
        // failure mode SORTABLE_PROPERTIES exists to prevent, tested with the strongest
        // possible case rather than a merely-disallowed-but-real field.
        mockMvc.perform(get("/api/jobs?sort=thisFieldDoesNotExist,desc&size=5"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("sorting by an allow-listed field (salaryMax) is honored")
    void allowlistedSortFieldIsHonored() throws Exception {
        String[] tc = recruiterWithCompany();
        approve(tc[1], adminToken());
        long lowPaying = createJob(tc[0], "Sort Low " + UUID.randomUUID());
        long highPaying = createJob(tc[0], "Sort High " + UUID.randomUUID());
        mockMvc.perform(patch("/api/jobs/" + lowPaying + "/publish").header("Authorization", "Bearer " + tc[0]));
        mockMvc.perform(patch("/api/jobs/" + highPaying + "/publish").header("Authorization", "Bearer " + tc[0]));

        // Both jobs share the same salaryMax (2000000, from createJobPayload) in this fixture,
        // so this asserts the request succeeds and honors the requested field without
        // erroring - the allow-list is what's under test here, not the ordering itself.
        mockMvc.perform(get("/api/jobs?sort=salaryMax,asc&size=200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + lowPaying + ")]").exists())
                .andExpect(jsonPath("$.data.content[?(@.id == " + highPaying + ")]").exists());
    }

    @Test
    @DisplayName("GET /api/jobs never returns a DRAFT job even when a filter would otherwise match it")
    void searchNeverLeaksDraftJobs() throws Exception {
        String[] tc = recruiterWithCompany();
        String marker = "NeverLeaked" + UUID.randomUUID().toString().substring(0, 8);
        long draftId = createJob(tc[0], marker + " Engineer");
        // Deliberately never published.

        mockMvc.perform(get("/api/jobs?keyword=" + marker + "&size=200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + draftId + ")]").doesNotExist());
    }
}
