package lk.sithiyama.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared plumbing for the HTTP-level integration tests. Runs against the
 * in-memory H2 "test" profile so the suite needs no Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ApiIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String registerAndGetToken(String prefix, String role) throws Exception {
        Map<String, Object> body = new HashMap<>();
        String username = prefix + UUID.randomUUID().toString().substring(0, 8);
        body.put("username", username);
        body.put("email", username + "@example.lk");
        body.put("password", "Password123!");
        body.put("displayName", "Test " + prefix);
        if (role != null) {
            body.put("role", role);
        }
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    protected String registerAndGetToken(String prefix) throws Exception {
        return registerAndGetToken(prefix, null);
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected String routeJson(String name, String visibility,
                               double startLat, double startLon,
                               double endLat, double endLon) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("description", "Integration test route");
        request.put("region", "Knuckles");
        request.put("difficulty", "MODERATE");
        request.put("visibility", visibility);
        request.put("tags", List.of("knuckles", "test"));
        request.put("path", List.of(
                Map.of("latitude", startLat, "longitude", startLon),
                Map.of("latitude", (startLat + endLat) / 2, "longitude", (startLon + endLon) / 2),
                Map.of("latitude", endLat, "longitude", endLon)));
        request.put("waypoints", List.of(
                Map.of("name", "Trailhead", "type", "TRAILHEAD", "latitude", startLat, "longitude", startLon),
                Map.of("name", "Summit", "type", "VIEWPOINT", "latitude", endLat, "longitude", endLon)));
        return objectMapper.writeValueAsString(request);
    }

    protected String uniqueName(String base) {
        return base + " " + UUID.randomUUID().toString().substring(0, 8);
    }
}
