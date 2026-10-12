package lk.sithiyama.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Map<String, Object> registration(String prefix) {
        String username = prefix + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", username + "@example.lk");
        body.put("password", "Password123!");
        body.put("displayName", "Test " + prefix);
        return body;
    }

    @Test
    void registerThenLoginThenReadProfile() throws Exception {
        Map<String, Object> registration = registration("hiker");

        String registerBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("ROLE_HIKER"))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(registerBody).get("accessToken").asText();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", registration.get("username"),
                                "password", "Password123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(registration.get("username")));
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        Map<String, Object> registration = registration("dupe");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsBadCredentials() throws Exception {
        Map<String, Object> registration = registration("creds");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", registration.get("username"),
                                "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidRegistrationPayload() throws Exception {
        Map<String, Object> invalid = new HashMap<>();
        invalid.put("username", "ab");
        invalid.put("email", "not-an-email");
        invalid.put("password", "short");
        invalid.put("displayName", "");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void refusesProtectedEndpointsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/tracks"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/trips"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cannotSelfAssignAdminRole() throws Exception {
        Map<String, Object> registration = registration("sneaky");
        registration.put("role", "ROLE_ADMIN");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration)))
                .andExpect(status().isBadRequest());
    }
}
