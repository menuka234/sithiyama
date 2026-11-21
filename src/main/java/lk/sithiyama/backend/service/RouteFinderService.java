package lk.sithiyama.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lk.sithiyama.backend.config.GeminiConfig;
import lk.sithiyama.backend.dto.AiDtos.AiHealthResponse;
import lk.sithiyama.backend.dto.AiDtos.RouteFinderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Maps a Sinhala / Singlish / English mental-health message to a Sithiyama
 * app route. Gemini is used when a key is available; a deterministic local
 * mapping keeps the endpoint useful without a key. Self-harm language is
 * intercepted before any model call and always routes to /emergency.
 */
@Service
public class RouteFinderService {

    private static final Logger log = LoggerFactory.getLogger(RouteFinderService.class);

    private static final Set<String> ALLOWED_ROUTES = Set.of(
            "/mood-tracker",
            "/meditation",
            "/meditation/sleep",
            "/breathing",
            "/journal",
            "/chat",
            "/therapist",
            "/sleep",
            "/emergency");

    private static final List<String> CRISIS_TERMS = List.of(
            "suicide", "suicidal", "self harm", "self-harm", "selfharm",
            "kill myself", "hurt myself", "end my life", "take my life",
            "sad", "depressed",
            "ආත්ම හිංසනය", "ආත්මඝාතනය", "මරාගන්න", "මැරෙන්න", "මරන්න",
            "ජීවිතේ නැති", "දුකයි", "දුක් වෙනවා", "හිත රිදෙනවා");

    private static final String SYSTEM_PROMPT = """
            You are Sithiyama, a kind, calm, non-judgmental mental health companion for Sri Lanka. User speaks Sinhala/Singlish/English. Understand their feeling and map to best route. Always return valid JSON only. Never give medical diagnosis. If user says sad / suicidal, route to /emergency and reply kindly to seek help.

            Return exactly this JSON shape and nothing else:
            {"intent":"...","route":"...","label":"...","reply":"...","params":{}}

            Routes you may choose from:
            /mood-tracker - Mood eka record karanna
            /meditation - Meditation karanna
            /meditation/sleep - Sleep Meditation (use for sleep meditation requests)
            /breathing - Breathing exercise
            /journal - Journal liyanna
            /chat - AI ekka katha karanna
            /therapist - Therapist kenek hoyanna
            /sleep - Ninda yana tips / sounds
            /emergency - Help oni nam

            Keep reply short, warm and in the user's own style (Sinhala, Singlish or English). params must be an object, usually {}.
            """;

    private final GeminiConfig geminiConfig;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public RouteFinderService(GeminiConfig geminiConfig,
                              @Qualifier("geminiRestClient") RestClient restClient,
                              ObjectMapper objectMapper,
                              @Value("${sithiyama.ai.route-finder.model:gemini-1.5-flash}") String model) {
        this.geminiConfig = geminiConfig;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.model = model == null || model.isBlank() ? "gemini-1.5-flash" : model.trim();
    }

    public RouteFinderResponse findRoute(String message, String headerKey) {
        String clean = message == null ? "" : message.trim();
        String lower = clean.toLowerCase(Locale.ROOT);

        // Safety first: never send a crisis message to the model for normal routing.
        if (containsAny(lower, CRISIS_TERMS)) {
            return emergency();
        }

        String key = geminiConfig.resolveKey(headerKey);
        if (!key.isEmpty()) {
            try {
                RouteFinderResponse geminiAnswer = callGemini(clean, key);
                if (geminiAnswer != null) {
                    // Re-check the model output: a crisis phrase must still be emergency.
                    if (containsAny(geminiAnswer.reply().toLowerCase(Locale.ROOT), CRISIS_TERMS)
                            && !"/emergency".equals(geminiAnswer.route())) {
                        return emergency();
                    }
                    return geminiAnswer;
                }
            } catch (RestClientResponseException ex) {
                log.warn("Gemini route finder failed with status {}: {}",
                        ex.getStatusCode().value(), truncate(ex.getResponseBodyAsString(), 200));
            } catch (Exception ex) {
                log.warn("Gemini route finder failed: {}", ex.getMessage());
            }
        }

        return local(lower);
    }

    public AiHealthResponse health(String headerKey) {
        return new AiHealthResponse(
                "ok",
                geminiConfig.hasKey(headerKey) ? "connected" : "not_configured");
    }

    // ------------------------------------------------------------------
    // Gemini
    // ------------------------------------------------------------------

    private RouteFinderResponse callGemini(String message, String key) {
        String resolvedKey = key;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", SYSTEM_PROMPT))));
        payload.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", message)))));
        payload.put("generationConfig", Map.of(
                "temperature", 0.2,
                "maxOutputTokens", 300,
                "responseMimeType", "application/json"));

        JsonNode response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/{model}:generateContent")
                        .queryParam("key", resolvedKey)
                        .build(model))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        String text = extractText(response);
        if (text.isBlank()) {
            return null;
        }
        return parse(stripCodeFence(text));
    }

    private static String extractText(JsonNode response) {
        if (response == null) {
            return "";
        }
        JsonNode candidates = response.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidates.get(0).path("content").path("parts")) {
            if (part.hasNonNull("text")) {
                text.append(part.get("text").asText());
            }
        }
        return text.toString().trim();
    }

    private RouteFinderResponse parse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String intent = textField(node, "intent");
            String route = textField(node, "route");
            String label = textField(node, "label");
            String reply = textField(node, "reply");

            if (intent == null || route == null || label == null || reply == null) {
                return null;
            }
            if (!ALLOWED_ROUTES.contains(route)) {
                return null;
            }

            Map<String, Object> params = Map.of();
            JsonNode paramsNode = node.get("params");
            if (paramsNode != null && paramsNode.isObject()) {
                Map<String, Object> converted = objectMapper.convertValue(
                        paramsNode, new TypeReference<Map<String, Object>>() {
                        });
                if (converted != null) {
                    params = converted;
                }
            }
            return new RouteFinderResponse(intent, route, label, truncate(reply, 600), params);
        } catch (Exception ex) {
            log.warn("Could not parse Gemini route finder JSON: {}", ex.getMessage());
            return null;
        }
    }

    private static String textField(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static String stripCodeFence(String raw) {
        String text = raw.trim();
        if (!text.startsWith("```")) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            return text;
        }
        text = text.substring(firstNewline + 1);
        int lastFence = text.lastIndexOf("```");
        if (lastFence >= 0) {
            text = text.substring(0, lastFence);
        }
        return text.trim();
    }

    // ------------------------------------------------------------------
    // Deterministic fallback and safety
    // ------------------------------------------------------------------

    private static RouteFinderResponse emergency() {
        return new RouteFinderResponse(
                "emergency_support",
                "/emergency",
                "Emergency Help",
                "Oba aashala naha — obaata help oni. Karuna karala 119 (police) ho "
                        + "local emergency number ekata call karanna. Mata me tana oba ekka katha karanna puluwan.",
                Map.of());
    }

    private static RouteFinderResponse local(String lower) {
        boolean sleep = containsAny(lower,
                "ninda", "nidda", "sleep", "insomnia", "නිදි", "නින්ද", "නිදාගන්න");
        boolean stress = containsAny(lower,
                "stress", "stressed", "anxious", "anxiety", "panic", "ආතති", "මානසික");
        boolean mood = containsAny(lower,
                "mood", "feeling", "මනෝභාව", "හැඟීම");
        boolean journal = containsAny(lower,
                "journal", "diary", "liyanna", "ලියන්න", "සටහන්");
        boolean therapist = containsAny(lower,
                "therapist", "counselor", "counsellor", "help", "උදව්", "සහාය");
        boolean chat = containsAny(lower,
                "chat", "talk", "katha", "කතා", "කතා කරන්න");

        if (sleep && stress) {
            return new RouteFinderResponse(
                    "meditation_for_sleep",
                    "/meditation/sleep",
                    "Sleep Meditation",
                    "Ninda yanna kalin me meditation eka karala balanna",
                    Map.of());
        }
        if (sleep) {
            return new RouteFinderResponse(
                    "sleep_tips",
                    "/sleep",
                    "Sleep Tips",
                    "Ninda yanna kalin phone eka thiyanna, hitha sanaghana karala, "
                            + "me tips ekka ninda yanna balanna.",
                    Map.of());
        }
        if (stress) {
            return new RouteFinderResponse(
                    "breathing_for_stress",
                    "/breathing",
                    "Breathing Exercise",
                    "Hada aran 4-7-8 breathing eka karanna. Obaata dhana ganna mata puluwan.",
                    Map.of());
        }
        if (journal) {
            return new RouteFinderResponse(
                    "write_journal",
                    "/journal",
                    "Journal",
                    "Mana hithuna dewal journal eke liyanna. Eken hithata padahalak damanna puluwan.",
                    Map.of());
        }
        if (mood) {
            return new RouteFinderResponse(
                    "record_mood",
                    "/mood-tracker",
                    "Mood Tracker",
                    "Aajja mood eka record karanna. Eken obaata kohomada dhenna puluwan.",
                    Map.of());
        }
        if (therapist) {
            return new RouteFinderResponse(
                    "find_therapist",
                    "/therapist",
                    "Find a Therapist",
                    "Obaata katha karanna qualified kenek hoyanna mata puluwan.",
                    Map.of());
        }
        if (chat) {
            return new RouteFinderResponse(
                    "talk_to_ai",
                    "/chat",
                    "Chat with Sithiyama",
                    "Mata katha karanna enna. Obaata kohomada dhenenne?",
                    Map.of());
        }
        return new RouteFinderResponse(
                "talk_to_ai",
                "/chat",
                "Chat with Sithiyama",
                "Mata katha karanna enna. Obaata kohomada dhenenne?",
                Map.of());
    }

    private static boolean containsAny(String lower, List<String> terms) {
        for (String term : terms) {
            if (containsTerm(lower, term)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String lower, String... terms) {
        return containsAny(lower, List.of(terms));
    }

    private static boolean containsTerm(String lower, String term) {
        String needle = term.toLowerCase(Locale.ROOT);
        boolean ascii = needle.chars().allMatch(c -> c < 128);
        if (!ascii) {
            return lower.contains(needle);
        }
        String regex = "(?<![\\p{L}\\p{N}])" + Pattern.quote(needle) + "(?![\\p{L}\\p{N}])";
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS)
                .matcher(lower)
                .find();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
