package lk.sithiyama.backend;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * The Gemini key is intentionally absent in the test profile, so these tests
 * cover the route-data fallback, validation and the authenticated boundary.
 */
class AiRouteAssistantIntegrationTest extends ApiIntegrationTest {

    private static final String AUTHORIZATION = HttpHeaders.AUTHORIZATION;

    @Test
    void answersBasicRouteQuestionsWithoutAGeminiKey() throws Exception {
        String token = registerAndGetToken("ai-guide");
        String name = uniqueName("AI Test Path");

        String created = mockMvc.perform(post("/api/v1/routes")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson(name, "PUBLIC", 7.4640, 80.7780, 7.4462, 80.7935)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String routeId = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(post("/api/v1/ai/routes/{id}/ask", routeId)
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"What checkpoints are on the way?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("local"))
                .andExpect(jsonPath("$.notice").value(containsString("Gemini is not configured")))
                .andExpect(jsonPath("$.answer").value(containsString("Trailhead")))
                .andExpect(jsonPath("$.answer").value(containsString("Summit")));

        mockMvc.perform(post("/api/v1/ai/routes/{id}/ask", routeId)
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"How difficult is this route?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(containsString("MODERATE")));
    }

    @Test
    void rejectsAnEmptyQuestionAndInvalidHistoryRole() throws Exception {
        String token = registerAndGetToken("ai-guide");
        String name = uniqueName("AI Validation Path");
        String created = mockMvc.perform(post("/api/v1/routes")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson(name, "PUBLIC", 7.4640, 80.7780, 7.4462, 80.7935)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String routeId = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(post("/api/v1/ai/routes/{id}/ask", routeId)
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":""}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/ai/routes/{id}/ask", routeId)
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"hello","history":[{"role":"system","text":"x"}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAnAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/v1/ai/routes/{id}/ask", "00000000-0000-0000-0000-000000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"hello"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
