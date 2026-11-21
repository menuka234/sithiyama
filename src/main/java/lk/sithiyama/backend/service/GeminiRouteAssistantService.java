package lk.sithiyama.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lk.sithiyama.backend.dto.AiDtos.ChatTurn;
import lk.sithiyama.backend.dto.AiDtos.RouteAnswerResponse;
import lk.sithiyama.backend.dto.AiDtos.RouteQuestionRequest;
import lk.sithiyama.backend.dto.RouteDtos.RouteResponse;
import lk.sithiyama.backend.dto.RouteDtos.WaypointResponse;
import lk.sithiyama.backend.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Answers questions about one route. Gemini is used when a key is configured;
 * otherwise the assistant falls back to facts that are already in the route
 * record, so the screen still works without a paid API key.
 */
@Service
public class GeminiRouteAssistantService {

    private static final Logger log = LoggerFactory.getLogger(GeminiRouteAssistantService.class);

    private static final int MAX_HISTORY_TURNS = 8;
    private static final int MAX_ANSWER_CHARS = 1_500;
    private static final int MAX_LISTED_WAYPOINTS = 20;

    private final RouteService routeService;
    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public GeminiRouteAssistantService(
            RouteService routeService,
            @Value("${sithiyama.ai.gemini.api-key:}") String apiKey,
            @Value("${sithiyama.ai.gemini.model:gemini-2.5-flash}") String model,
            @Value("${sithiyama.ai.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${sithiyama.ai.gemini.timeout-seconds:30}") int timeoutSeconds) {
        this.routeService = routeService;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gemini-2.5-flash" : model.trim();
        String configuredBaseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://generativelanguage.googleapis.com"
                : baseUrl.trim();
        this.baseUrl = configuredBaseUrl.endsWith("/")
                ? configuredBaseUrl.substring(0, configuredBaseUrl.length() - 1)
                : configuredBaseUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(Math.max(5, timeoutSeconds) * 1_000);
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    public String model() {
        return model;
    }

    public RouteAnswerResponse ask(UUID routeId, RouteQuestionRequest request) {
        // RouteService.get() also enforces the route's visibility rules.
        RouteResponse route = routeService.get(routeId);
        String question = request.question().trim();
        List<ChatTurn> history = sanitizeHistory(request.history());

        if (!isEnabled()) {
            return fallback(route, question,
                    "Gemini is not configured on the server, so this answer uses the saved route data only.");
        }

        try {
            String answer = callGemini(route, question, history);
            if (answer == null || answer.isBlank()) {
                return fallback(route, question,
                        "Gemini returned an empty answer, so this answer uses the saved route data only.");
            }
            return new RouteAnswerResponse(route.id(), answer, model, "gemini", null);
        } catch (RestClientResponseException ex) {
            log.warn("Gemini request failed with status {}: {}", ex.getStatusCode().value(),
                    truncate(ex.getResponseBodyAsString(), 300));
            return fallback(route, question,
                    "Gemini is unavailable right now, so this answer uses the saved route data only.");
        } catch (Exception ex) {
            log.warn("Gemini request failed: {}", ex.getMessage());
            return fallback(route, question,
                    "Gemini is unavailable right now, so this answer uses the saved route data only.");
        }
    }

    // ------------------------------------------------------------------
    // Gemini
    // ------------------------------------------------------------------

    private String callGemini(RouteResponse route, String question, List<ChatTurn> history) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatTurn turn : history) {
            contents.add(Map.of(
                    "role", turn.role(),
                    "parts", List.of(Map.of("text", turn.text()))));
        }
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", question))));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", systemPrompt(route)))));
        payload.put("contents", contents);
        payload.put("generationConfig", Map.of(
                "temperature", 0.4,
                "maxOutputTokens", 700));

        JsonNode response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/{model}:generateContent")
                        .queryParam("key", apiKey)
                        .build(model))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            return null;
        }

        JsonNode candidates = response.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return null;
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        StringBuilder answer = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.hasNonNull("text")) {
                answer.append(part.get("text").asText());
            }
        }
        return truncate(answer.toString().trim(), MAX_ANSWER_CHARS);
    }

    private String systemPrompt(RouteResponse route) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are SITHIYAMA's trail assistant for hiking routes in Sri Lanka.\n")
                .append("Answer the user's question using only the route data below. ")
                .append("If the data does not contain the answer, say you do not have that detail ")
                .append("and suggest what the user can check. Never invent distances, times, ")
                .append("hazards, or safety guarantees. For safety-critical questions, advise ")
                .append("checking with local guides or authorities. Keep the answer under 120 words, ")
                .append("friendly and practical.\n\n")
                .append("ROUTE DATA\n")
                .append("Name: ").append(orUnknown(route.name())).append('\n')
                .append("Region: ").append(orUnknown(route.region())).append('\n')
                .append("Difficulty: ").append(orUnknown(route.difficulty())).append('\n')
                .append("Distance: ").append(formatMeters(route.distanceMeters())).append('\n')
                .append("Ascent: ").append(formatMeters(route.ascentMeters())).append('\n')
                .append("Estimated duration: ").append(formatMinutes(route.estimatedDurationMinutes())).append('\n')
                .append("Tags: ").append(tags(route)).append('\n')
                .append("Start: ").append(formatCoordinate(route.startLatitude(), route.startLongitude())).append('\n')
                .append("End: ").append(route.endLatitude() == null
                        ? "not recorded"
                        : formatCoordinate(route.endLatitude(), route.endLongitude()))
                .append('\n');

        if (route.description() != null && !route.description().isBlank()) {
            prompt.append("Description: ")
                    .append(truncate(route.description().trim(), 2_000))
                    .append('\n');
        }

        List<WaypointResponse> waypoints = route.waypoints() == null ? List.of() : route.waypoints();
        prompt.append("Checkpoints (").append(waypoints.size()).append("):\n");
        if (waypoints.isEmpty()) {
            prompt.append("- none recorded\n");
        } else {
            int limit = Math.min(waypoints.size(), MAX_LISTED_WAYPOINTS);
            for (int i = 0; i < limit; i++) {
                WaypointResponse waypoint = waypoints.get(i);
                prompt.append(i + 1).append(". ").append(orUnknown(waypoint.name()));
                if (waypoint.type() != null) {
                    prompt.append(" [").append(waypoint.type()).append(']');
                }
                prompt.append(" at ").append(formatCoordinate(waypoint.latitude(), waypoint.longitude()));
                if (waypoint.note() != null && !waypoint.note().isBlank()) {
                    prompt.append(" - ").append(truncate(waypoint.note().trim(), 200));
                }
                prompt.append('\n');
            }
            if (waypoints.size() > limit) {
                prompt.append("- ... ").append(waypoints.size() - limit).append(" more\n");
            }
        }
        return prompt.toString();
    }

    // ------------------------------------------------------------------
    // Local fallback
    // ------------------------------------------------------------------

    private RouteAnswerResponse fallback(RouteResponse route, String question, String notice) {
        return new RouteAnswerResponse(route.id(), localAnswer(route, question), null, "local", notice);
    }

    private String localAnswer(RouteResponse route, String question) {
        String q = question.toLowerCase(Locale.ROOT);

        boolean asksDuration = containsAny(q,
                "how long does", "how long will", "how long would", "take to walk", "take to hike");
        boolean asksDistance = containsAny(q,
                "distance", "how far", "how long is", "length", " km", "kilomet");
        boolean asksTime = containsAny(q, "time", "duration", "hours", "minutes")
                || (q.contains("long") && !asksDistance);
        boolean asksDifficulty = containsAny(q,
                "difficult", "hard", "easy", "beginner", "suitable", "fitness", "level");
        boolean asksCheckpoints = containsAny(q,
                "checkpoint", "waypoint", "stop", "on the way", "along the way");
        boolean asksStart = containsAny(q, "start", "begin", "trailhead");
        boolean asksEnd = containsAny(q, "end", "finish");
        boolean asksAscent = containsAny(q, "elevation", "ascent", "climb", "uphill");
        boolean asksRegion = containsAny(q, "where", "region", "location", "area");
        boolean asksTags = containsAny(q, "tag", "terrain", "feature");

        List<String> parts = new ArrayList<>();

        if (asksDuration || asksTime) {
            parts.add("The estimated walking time is "
                    + formatMinutes(route.estimatedDurationMinutes()) + '.');
        }
        if (asksDistance) {
            parts.add(route.name() + " is " + formatMeters(route.distanceMeters()) + " long.");
        }
        if (asksDifficulty) {
            parts.add("The recorded difficulty is " + orUnknown(route.difficulty()) + ", with "
                    + formatMeters(route.ascentMeters()) + " of ascent. Check the checkpoint notes "
                    + "and ask a local guide if you are unsure about fitness or conditions.");
        }
        if (asksCheckpoints) {
            parts.add(checkpointSummary(route));
        }
        if (asksStart && asksEnd) {
            parts.add("The route starts at " + formatCoordinate(route.startLatitude(), route.startLongitude())
                    + (route.endLatitude() == null
                    ? " and the end point is not recorded."
                    : " and ends at " + formatCoordinate(route.endLatitude(), route.endLongitude()) + '.'));
        } else if (asksStart) {
            parts.add("The route starts at "
                    + formatCoordinate(route.startLatitude(), route.startLongitude()) + '.');
        } else if (asksEnd) {
            parts.add(route.endLatitude() == null
                    ? "The end point is not recorded for this route."
                    : "The route ends at "
                    + formatCoordinate(route.endLatitude(), route.endLongitude()) + '.');
        } else if (asksRegion) {
            parts.add("The route is in " + orUnknown(route.region()) + '.');
        }
        if (asksAscent && !asksDifficulty) {
            parts.add("The recorded ascent is " + formatMeters(route.ascentMeters()) + '.');
        }
        if (asksTags) {
            parts.add("This route is tagged: " + tags(route) + '.');
        }

        if (parts.isEmpty()) {
            return "I can answer basic facts about " + route.name()
                    + ": distance, difficulty, duration, ascent, checkpoints, and start/end. "
                    + "For free-form questions, add GEMINI_API_KEY on the server.";
        }
        return String.join(" ", parts.subList(0, Math.min(parts.size(), 3)));
    }

    private String checkpointSummary(RouteResponse route) {
        List<WaypointResponse> waypoints = route.waypoints() == null ? List.of() : route.waypoints();
        if (waypoints.isEmpty()) {
            return "No checkpoints are recorded for this route.";
        }
        StringBuilder answer = new StringBuilder("Checkpoints on this route: ");
        int limit = Math.min(waypoints.size(), 8);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                answer.append("; ");
            }
            answer.append(i + 1).append(". ").append(orUnknown(waypoints.get(i).name()));
        }
        if (waypoints.size() > limit) {
            answer.append("; and ").append(waypoints.size() - limit).append(" more");
        }
        return answer.append('.').toString();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private List<ChatTurn> sanitizeHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<ChatTurn> clean = new ArrayList<>();
        for (ChatTurn turn : history) {
            if (turn == null || turn.text() == null || turn.text().isBlank()) {
                continue;
            }
            String role = turn.role() == null ? "" : turn.role().trim().toLowerCase(Locale.ROOT);
            if (!role.equals("user") && !role.equals("model")) {
                throw new BadRequestException("History role must be 'user' or 'model'");
            }
            clean.add(new ChatTurn(role, truncate(turn.text().trim(), 2_000)));
        }
        int start = Math.max(0, clean.size() - MAX_HISTORY_TURNS);
        return clean.subList(start, clean.size());
    }

    private static String tags(RouteResponse route) {
        return route.tags() == null || route.tags().isEmpty()
                ? "none"
                : String.join(", ", route.tags());
    }

    private static String formatMeters(double meters) {
        if (meters >= 1_000) {
            return String.format(Locale.ROOT, "%.1f km", meters / 1_000.0);
        }
        return Math.round(meters) + " m";
    }

    private static String formatMinutes(int minutes) {
        if (minutes >= 60) {
            int hours = minutes / 60;
            int rest = minutes % 60;
            return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
        }
        return minutes + " min";
    }

    private static String formatCoordinate(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude);
    }

    private static String orUnknown(Object value) {
        if (value == null) {
            return "not recorded";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "not recorded" : text;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
