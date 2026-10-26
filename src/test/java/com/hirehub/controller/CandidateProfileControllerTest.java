package com.hirehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hirehub.entity.Role;
import com.hirehub.entity.User;
import com.hirehub.entity.enums.RoleName;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@code @PreAuthorize} through the real filter chain, the same way
 * {@code AuthControllerTest} exercises authentication: a token from a real
 * register/login call, sent as a real header, on a real request. No {@code @WithMockUser}.
 */
@AutoConfigureMockMvc
class CandidateProfileControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String uniqueEmail() {
        return "candidate-test-" + UUID.randomUUID() + "@hirehub.test";
    }

    private String registerAndGetToken(String email, String role) throws Exception {
        String payload = """
                {"email":"%s","password":"Password123","fullName":"Test User","role":"%s"}
                """.formatted(email, role);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    /**
     * ADMIN cannot come from {@code /auth/register} by design (Phase 4B), so an admin
     * principal for these tests is seeded directly through the repositories - which is also
     * exactly how a real admin account would be provisioned in this system. The insert
     * commits immediately (Spring Data repository methods are transactional on their own),
     * which is required here anyway: the login call right after is a separate HTTP request
     * and must see this row as already committed.
     */
    private String seedAdminAndGetToken() throws Exception {
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        String email = uniqueEmail();
        User admin = new User(email, passwordEncoder.encode("Password123"), "Test Admin", adminRole);
        userRepository.save(admin);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("a freshly registered candidate has an empty-but-valid profile")
    void freshCandidateHasEmptyProfile() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "CANDIDATE");

        mockMvc.perform(get("/api/candidates/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headline").doesNotExist())
                .andExpect(jsonPath("$.data.skills").isArray())
                .andExpect(jsonPath("$.data.skills").isEmpty());
    }

    @Test
    @DisplayName("a recruiter cannot read the candidate /me endpoint - 403, not 404 or 200")
    void recruiterCannotAccessCandidateMe() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "RECRUITER");

        mockMvc.perform(get("/api/candidates/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("updating the profile persists and is visible on a subsequent read")
    void updateProfilePersists() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "CANDIDATE");
        String auth = "Bearer " + token;

        mockMvc.perform(put("/api/candidates/me").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"headline":"Senior Backend Engineer","experienceYears":6,
                                 "currentLocation":"Pune","resumeUrl":"https://files.example.test/r.pdf"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headline").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.data.experienceYears").value(6));

        mockMvc.perform(get("/api/candidates/me").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headline").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.data.currentLocation").value("Pune"))
                .andExpect(jsonPath("$.data.resumeUrl").value("https://files.example.test/r.pdf"));
    }

    @Test
    @DisplayName("an invalid update (bad URL) is rejected with a field-level error")
    void updateWithInvalidUrlIsRejected() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "CANDIDATE");

        mockMvc.perform(put("/api/candidates/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resumeUrl":"not-a-url"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.resumeUrl").exists());
    }

    @Test
    @DisplayName("adding a new skill creates it and attaches it to the profile")
    void addNewSkill() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "CANDIDATE");
        String uniqueSkillName = "distinctive-skill-" + UUID.randomUUID();

        mockMvc.perform(post("/api/candidates/me/skills").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillName":"%s","proficiency":"ADVANCED","yearsOfExperience":3}
                                """.formatted(uniqueSkillName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.skills[0].skill.name").value(uniqueSkillName))
                .andExpect(jsonPath("$.data.skills[0].proficiency").value("ADVANCED"));
    }

    @Test
    @DisplayName("adding a skill by a different-cased name reuses the seeded skill, not a duplicate")
    void addSkillReusesSeededSkillRegardlessOfCase() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "CANDIDATE");

        mockMvc.perform(post("/api/candidates/me/skills").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillName":"JAVA","proficiency":"EXPERT","yearsOfExperience":5}
                                """))
                .andExpect(status().isCreated())
                // Normalized to lower case, matching the seeded "java" row from V2 -
                // proves find-or-create found the existing row rather than making a new one.
                .andExpect(jsonPath("$.data.skills[0].skill.name").value("java"));
    }

    @Test
    @DisplayName("adding the same skill twice is rejected as a duplicate")
    void addingSameSkillTwiceIsRejected() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "CANDIDATE");
        String payload = """
                {"skillName":"python","proficiency":"INTERMEDIATE","yearsOfExperience":2}
                """;

        mockMvc.perform(post("/api/candidates/me/skills").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/candidates/me/skills").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("removing a skill takes it off the profile; removing it again is a 404")
    void removeSkillThenRemoveAgainIsNotFound() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "CANDIDATE");
        String auth = "Bearer " + token;

        String addResponse = mockMvc.perform(post("/api/candidates/me/skills").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillName":"kubernetes","proficiency":"BEGINNER","yearsOfExperience":1}
                                """))
                .andReturn().getResponse().getContentAsString();

        JsonNode skillNode = objectMapper.readTree(addResponse).path("data").path("skills").get(0).path("skill");
        long skillId = skillNode.path("id").asLong();

        mockMvc.perform(delete("/api/candidates/me/skills/" + skillId).header("Authorization", auth))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/candidates/me/skills").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(delete("/api/candidates/me/skills/" + skillId).header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("a recruiter can view a candidate's profile by id")
    void recruiterCanViewCandidateById() throws Exception {
        String candidateEmail = uniqueEmail();
        registerAndGetToken(candidateEmail, "CANDIDATE");

        String recruiterToken = registerAndGetToken(uniqueEmail(), "RECRUITER");

        // Fetch the candidate's own profile id via their own /me call rather than assuming
        // profile id == user id (they are independent surrogate keys - see DATABASE.md).
        String candidateLoginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123"}
                                """.formatted(candidateEmail)))
                .andReturn().getResponse().getContentAsString();
        String candidateToken = objectMapper.readTree(candidateLoginResponse).path("data").path("accessToken").asText();
        String meResponse = mockMvc.perform(get("/api/candidates/me").header("Authorization", "Bearer " + candidateToken))
                .andReturn().getResponse().getContentAsString();
        long profileId = objectMapper.readTree(meResponse).path("data").path("id").asLong();

        mockMvc.perform(get("/api/candidates/" + profileId).header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(candidateEmail));
    }

    @Test
    @DisplayName("a candidate cannot view another candidate's profile by id")
    void candidateCannotViewProfileById() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "CANDIDATE");

        mockMvc.perform(get("/api/candidates/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin viewing a non-existent candidate profile gets 404, not 500")
    void adminViewingMissingProfileGetsNotFound() throws Exception {
        String adminToken = seedAdminAndGetToken();

        mockMvc.perform(get("/api/candidates/999999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }
}
