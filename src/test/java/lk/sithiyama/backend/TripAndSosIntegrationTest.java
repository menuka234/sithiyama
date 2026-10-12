package lk.sithiyama.backend;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class TripAndSosIntegrationTest extends ApiIntegrationTest {

    private static final String AUTHORIZATION = HttpHeaders.AUTHORIZATION;

    @Test
    void plansATripRecordsCheckInsAndTriagesAnSos() throws Exception {
        String hiker = registerAndGetToken("hiker");
        String otherHiker = registerAndGetToken("hiker");
        String ranger = registerAndGetToken("ranger", "ROLE_RANGER");

        Map<String, Object> tripRequest = new HashMap<>();
        tripRequest.put("title", uniqueName("Knuckles day hike"));
        tripRequest.put("notes", "Meeting at 05:00");
        tripRequest.put("plannedStartAt", Instant.now().toString());
        tripRequest.put("plannedEndAt", Instant.now().plus(8, ChronoUnit.HOURS).toString());
        tripRequest.put("groupSize", 8);
        tripRequest.put("checkInIntervalMinutes", 60);
        tripRequest.put("emergencyContactName", "Amma");
        tripRequest.put("emergencyContactPhone", "+94770000000");
        tripRequest.put("members", List.of(
                Map.of("name", "Trail Leader", "teamLead", true),
                Map.of("name", "Follower")));

        String tripId = objectMapper.readTree(mockMvc.perform(post("/api/v1/trips")
                        .header(AUTHORIZATION, bearer(hiker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tripRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.nextCheckInDueAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // A check-in proves life and flips the trip to ACTIVE.
        mockMvc.perform(post("/api/v1/trips/{id}/check-ins", tripId)
                        .header(AUTHORIZATION, bearer(hiker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":7.45,"longitude":80.78,"batteryPercent":72,"message":"All good"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("All good"));

        mockMvc.perform(get("/api/v1/trips/{id}", tripId).header(AUTHORIZATION, bearer(hiker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.lastCheckInAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/trips/{id}/check-ins", tripId).header(AUTHORIZATION, bearer(hiker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].batteryPercent").value(72));

        // Another hiker must not be able to read somebody else's trip.
        mockMvc.perform(get("/api/v1/trips/{id}", tripId).header(AUTHORIZATION, bearer(otherHiker)))
                .andExpect(status().isForbidden());

        // Raise an SOS the way a Companion Node would: position, battery and trail.
        String sosId = objectMapper.readTree(mockMvc.perform(post("/api/v1/sos")
                        .header(AUTHORIZATION, bearer(hiker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tripId":"%s","source":"NODE","latitude":7.45,"longitude":80.78,
                                 "batteryPercent":18,"message":"Fall detected, no response",
                                 "trail":[{"latitude":7.46,"longitude":80.78},{"latitude":7.45,"longitude":80.78}]}
                                """.formatted(tripId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.source").value("NODE"))
                .andExpect(jsonPath("$.trail.length()").value(2))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // Only operators triage alerts.
        mockMvc.perform(post("/api/v1/sos/{id}/acknowledge", sosId).header(AUTHORIZATION, bearer(hiker)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/sos/{id}/acknowledge", sosId).header(AUTHORIZATION, bearer(ranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedByName").isNotEmpty());

        mockMvc.perform(patch("/api/v1/sos/{id}/status", sosId)
                        .header(AUTHORIZATION, bearer(ranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"RESOLVED","note":"Group walked out at dawn"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        // An unrelated hiker cannot read a live alert.
        mockMvc.perform(get("/api/v1/sos/{id}", sosId).header(AUTHORIZATION, bearer(otherHiker)))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadsATrackFromTheNodeAndComputesDistance() throws Exception {
        String hiker = registerAndGetToken("hiker");
        String now = Instant.now().toString();

        String body = objectMapper.writeValueAsString(Map.of(
                "name", uniqueName("Node breadcrumb log"),
                "source", "NODE",
                "status", "COMPLETED",
                "startedAt", now,
                "points", List.of(
                        Map.of("latitude", 7.4640, "longitude", 80.7780, "batteryPercent", 90, "recordedAt", now),
                        Map.of("latitude", 7.4588, "longitude", 80.7845, "batteryPercent", 85, "recordedAt", now),
                        Map.of("latitude", 7.4505, "longitude", 80.7910, "batteryPercent", 80, "recordedAt", now))));

        mockMvc.perform(post("/api/v1/tracks")
                        .header(AUTHORIZATION, bearer(hiker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pointCount").value(3))
                .andExpect(jsonPath("$.distanceMeters").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.path.length()").value(3))
                .andExpect(jsonPath("$.source").value("NODE"));

        mockMvc.perform(get("/api/v1/tracks").header(AUTHORIZATION, bearer(hiker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].pointCount").value(3));
    }

    @Test
    void registersANodeAndAcceptsHeartbeats() throws Exception {
        String hiker = registerAndGetToken("guide");
        String code = "RAK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String nodeId = objectMapper.readTree(mockMvc.perform(post("/api/v1/nodes")
                        .header(AUTHORIZATION, bearer(hiker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "deviceCode", code,
                                "label", "Group node",
                                "firmwareVersion", "0.1.0"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deviceCode").value(code))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/nodes/{id}/heartbeat", nodeId)
                        .header(AUTHORIZATION, bearer(hiker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"batteryPercent":88,"latitude":7.45,"longitude":80.78,"firmwareVersion":"0.2.0"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batteryPercent").value(88))
                .andExpect(jsonPath("$.firmwareVersion").value("0.2.0"))
                .andExpect(jsonPath("$.lastSeenAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/nodes").header(AUTHORIZATION, bearer(hiker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deviceCode").value(code));
    }
}
