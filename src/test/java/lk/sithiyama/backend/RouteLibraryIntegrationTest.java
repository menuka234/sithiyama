package lk.sithiyama.backend;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class RouteLibraryIntegrationTest extends ApiIntegrationTest {

    private static final String AUTHORIZATION = HttpHeaders.AUTHORIZATION;
    // A point inside the Knuckles range, used for near-search assertions.
    private static final double KNUCKLES_LAT = 7.4500;
    private static final double KNUCKLES_LON = 80.7850;

    @Test
    void publishedRouteIsVisibleToAnonymousUsers() throws Exception {
        String token = registerAndGetToken("guide");
        String name = uniqueName("Dothalugala Test Path");

        String created = mockMvc.perform(post("/api/v1/routes")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson(name, "PUBLIC", 7.4640, 80.7780, 7.4462, 80.7935)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.distanceMeters").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.estimatedDurationMinutes").value(greaterThan(0)))
                .andExpect(jsonPath("$.waypoints.length()").value(2))
                .andExpect(jsonPath("$.tags").isArray())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(created);
        String id = node.get("id").asText();

        // Anonymous read of the single route, including polyline and waypoints.
        mockMvc.perform(get("/api/v1/routes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.path.length()").value(3))
                .andExpect(jsonPath("$.waypoints[1].name").value("Summit"));

        // Anonymous library search finds it by text.
        mockMvc.perform(get("/api/v1/routes").param("q", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value(name));
    }

    @Test
    void privateRouteIsHiddenFromAnonymousUsers() throws Exception {
        String token = registerAndGetToken("guide");
        String name = uniqueName("Secret Path");

        String created = mockMvc.perform(post("/api/v1/routes")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson(name, "PRIVATE", 7.5000, 80.7500, 7.5100, 80.7600)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(get("/api/v1/routes/{id}", id))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/routes/{id}", id).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));

        // The owner can find it again through the "mine" filter.
        mockMvc.perform(get("/api/v1/routes")
                        .param("mine", "true")
                        .param("q", name)
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value(name));
    }

    @Test
    void otherUsersCannotModifyARoute() throws Exception {
        String ownerToken = registerAndGetToken("owner");
        String otherToken = registerAndGetToken("intruder");
        String name = uniqueName("Guarded Path");

        String created = mockMvc.perform(post("/api/v1/routes")
                        .header(AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson(name, "PUBLIC", 7.5200, 80.7400, 7.5300, 80.7500)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(put("/api/v1/routes/{id}", id)
                        .header(AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson(name, "PUBLIC", 7.5200, 80.7400, 7.5300, 80.7500)))
                .andExpect(status().isForbidden());
    }

    @Test
    void radiusSearchFindsRoutesOnTheBoundingBoxFallback() throws Exception {
        String token = registerAndGetToken("guide");
        String name = uniqueName("Nearby Path");

        mockMvc.perform(post("/api/v1/routes")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson(name, "PUBLIC", 7.4460, 80.7800, 7.4550, 80.7900)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/routes")
                        .param("nearLat", String.valueOf(KNUCKLES_LAT))
                        .param("nearLon", String.valueOf(KNUCKLES_LON))
                        .param("radiusMeters", "20000")
                        .param("q", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value(name));

        // A radius on the other side of the island must not match.
        mockMvc.perform(get("/api/v1/routes")
                        .param("nearLat", "6.9271")
                        .param("nearLon", "79.8612")
                        .param("radiusMeters", "1000")
                        .param("q", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void rejectsAPathWithFewerThanTwoPoints() throws Exception {
        String token = registerAndGetToken("guide");
        String body = """
                {"name":"Broken","path":[{"latitude":7.4,"longitude":80.7}]}
                """;

        mockMvc.perform(post("/api/v1/routes")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
