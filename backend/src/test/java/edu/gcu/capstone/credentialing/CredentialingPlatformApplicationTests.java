package edu.gcu.capstone.credentialing;

import edu.gcu.capstone.credentialing.ml.AnomalyDetectionService;
import edu.gcu.capstone.credentialing.verification.NppesClient;
import edu.gcu.capstone.credentialing.verification.OigExclusionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CredentialingPlatformApplicationTests.java
 *
 * <p>Start-up and web-layer tests. The context test confirms that every bean
 * wires and that the datasets and the model load during start-up, which is the
 * fastest way to catch a configuration mistake. The remaining tests confirm the
 * access rules hold at the HTTP boundary: an unauthenticated caller is refused,
 * and a valid sign-in is accepted.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class CredentialingPlatformApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NppesClient nppesClient;

    @Autowired
    private OigExclusionService exclusionService;

    @Autowired
    private AnomalyDetectionService anomalyDetectionService;

    @Test
    @DisplayName("The application context starts and every dataset and model loads")
    void contextLoads() {
        assertThat(nppesClient.offlineRecordCount()).isGreaterThan(0);
        assertThat(exclusionService.isListLoaded()).isTrue();
        assertThat(anomalyDetectionService.getEvaluation()).isNotNull();
    }

    @Test
    @DisplayName("The health endpoint is reachable without signing in")
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("A protected endpoint refuses an unauthenticated caller")
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/providers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Valid credentials return a session token and the caller's role")
    void signInSucceedsWithSeededAccount() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reviewer\",\"password\":\"Review#2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("REVIEWER"));
    }

    @Test
    @DisplayName("Incorrect credentials are refused")
    void signInFailsWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reviewer\",\"password\":\"not-the-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An empty sign-in submission is rejected as a validation failure")
    void signInRejectsEmptySubmission() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A reviewer may not reach administrator endpoints")
    void reviewerIsRefusedAdminEndpoints() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reviewer\",\"password\":\"Review#2026\"}"))
                .andReturn().getResponse().getContentAsString();

        String token = body.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/admin/health").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
