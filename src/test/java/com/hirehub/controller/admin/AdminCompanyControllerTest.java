package com.hirehub.controller.admin;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminCompanyControllerTest extends AbstractIntegrationTest {

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
        return "admin-test-" + UUID.randomUUID() + "@hirehub.test";
    }

    private String tokenFor(String email, RoleName role) throws Exception {
        if (role == RoleName.ADMIN) {
            Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
            User admin = new User(email, passwordEncoder.encode("Password123"), "Test Admin", adminRole);
            userRepository.save(admin);
        } else {
            mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"email":"%s","password":"Password123","fullName":"Test User","role":"%s"}
                            """.formatted(email, role)));
        }
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private long createPendingCompany(String recruiterToken, String name) throws Exception {
        String response = mockMvc.perform(post("/api/companies").header("Authorization", "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    @Test
    @DisplayName("a recruiter cannot access the admin namespace at all")
    void recruiterCannotAccessAdminNamespace() throws Exception {
        String recruiterToken = tokenFor(uniqueEmail(), RoleName.RECRUITER);

        mockMvc.perform(get("/api/admin/companies").header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("an unauthenticated request to the admin namespace is 401, not 403")
    void anonymousRequestToAdminNamespaceIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/companies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("an admin sees a newly created company in the PENDING queue")
    void pendingQueueIncludesNewCompany() throws Exception {
        String recruiterToken = tokenFor(uniqueEmail(), RoleName.RECRUITER);
        String companyName = "Pending Co " + UUID.randomUUID();
        long companyId = createPendingCompany(recruiterToken, companyName);

        String adminToken = tokenFor(uniqueEmail(), RoleName.ADMIN);

        mockMvc.perform(get("/api/admin/companies?status=PENDING&size=200")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + companyId + ")]").exists());
    }

    @Test
    @DisplayName("approving a pending company flips its status")
    void approvingCompanyFlipsStatus() throws Exception {
        String recruiterToken = tokenFor(uniqueEmail(), RoleName.RECRUITER);
        long companyId = createPendingCompany(recruiterToken, "Approve Me " + UUID.randomUUID());
        String adminToken = tokenFor(uniqueEmail(), RoleName.ADMIN);

        mockMvc.perform(patch("/api/admin/companies/" + companyId + "/approval")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(get("/api/companies/" + companyId))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("rejecting a pending company flips its status to REJECTED")
    void rejectingCompanyFlipsStatus() throws Exception {
        String recruiterToken = tokenFor(uniqueEmail(), RoleName.RECRUITER);
        long companyId = createPendingCompany(recruiterToken, "Reject Me " + UUID.randomUUID());
        String adminToken = tokenFor(uniqueEmail(), RoleName.ADMIN);

        mockMvc.perform(patch("/api/admin/companies/" + companyId + "/approval")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"REJECTED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("deciding an already-decided company a second time is a 409, not a silent no-op")
    void decidingTwiceIsRejected() throws Exception {
        String recruiterToken = tokenFor(uniqueEmail(), RoleName.RECRUITER);
        long companyId = createPendingCompany(recruiterToken, "Decide Once " + UUID.randomUUID());
        String adminToken = tokenFor(uniqueEmail(), RoleName.ADMIN);
        String approvalPayload = """
                {"decision":"APPROVED"}
                """;

        mockMvc.perform(patch("/api/admin/companies/" + companyId + "/approval")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(approvalPayload))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/companies/" + companyId + "/approval")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(approvalPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));
    }
}
