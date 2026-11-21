package lk.sithiyama.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * No Gemini key is configured in the test profile, so these tests cover the
 * deterministic mapping, the safety interception and the health contract.
 */
class RouteFinderIntegrationTest extends ApiIntegrationTest {

    @Test
    void mapsSinhalaSinglishStressAndSleepToSleepMeditation() throws Exception {
        mockMvc.perform(post("/api/ai/find-route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"mata stress, ninda yanne na"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("meditation_for_sleep"))
                .andExpect(jsonPath("$.route").value("/meditation/sleep"))
                .andExpect(jsonPath("$.label").value("Sleep Meditation"))
                .andExpect(jsonPath("$.reply").value("Ninda yanna kalin me meditation eka karala balanna"))
                .andExpect(jsonPath("$.params").isMap());
    }

    @Test
    void mapsEnglishAnxietyToBreathing() throws Exception {
        mockMvc.perform(post("/api/ai/find-route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"I feel anxious and stressed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("/breathing"))
                .andExpect(jsonPath("$.label").value("Breathing Exercise"));
    }

    @Test
    void routesSelfHarmLanguageToEmergencyWithoutCallingGemini() throws Exception {
        mockMvc.perform(post("/api/ai/find-route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"mata suicide karanna oni"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("emergency_support"))
                .andExpect(jsonPath("$.route").value("/emergency"))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("119")));
    }

    @Test
    void routesSadnessToEmergencyAsRequired() throws Exception {
        mockMvc.perform(post("/api/ai/find-route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"I am sad"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("/emergency"));
    }

    @Test
    void healthReportsNotConfiguredWhenNoKeyIsAvailable() throws Exception {
        mockMvc.perform(get("/api/ai/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.gemini").value("not_configured"));
    }

    @Test
    void healthAcceptsPerRequestHeaderKey() throws Exception {
        mockMvc.perform(get("/api/ai/health")
                        .header("x-gemini-key", "test-only-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.gemini").value("connected"));
    }

    @Test
    void rejectsAnEmptyMessage() throws Exception {
        mockMvc.perform(post("/api/ai/find-route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void doesNotExposeTheHeaderKeyInTheResponse() throws Exception {
        String body = mockMvc.perform(post("/api/ai/find-route")
                        .header("x-gemini-key", "super-secret-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"journal liyanna oni"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        if (body.contains("super-secret-test-key")) {
            throw new AssertionError("API key leaked into the response: " + body);
        }
    }
}
