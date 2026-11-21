package lk.sithiyama.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Payloads for the Gemini-powered route assistant. */
public final class AiDtos {

    private AiDtos() {
    }

    /** One prior turn in the conversation. Gemini only accepts "user" and "model". */
    public record ChatTurn(
            @NotBlank
            @Pattern(regexp = "user|model", message = "role must be user or model")
            String role,

            @NotBlank
            @Size(max = 2_000)
            String text) {
    }

    public record RouteQuestionRequest(
            @NotBlank
            @Size(max = 1_000)
            String question,

            @Valid
            @Size(max = 8)
            List<ChatTurn> history) {
    }

    public record RouteAnswerResponse(
            UUID routeId,
            String answer,
            String model,
            /** "gemini" when Gemini answered, "local" for the built-in route facts. */
            String source,
            /** Optional explanation shown above the answer (for example, AI not configured). */
            String notice) {
    }

    public record AiStatusResponse(boolean enabled, String model) {
    }

    /** Request for the mental-health route finder. */
    public record FindRouteRequest(
            @NotBlank
            @Size(max = 1_000)
            String message) {
    }

    /**
     * JSON-only answer returned by /api/ai/find-route. No key or other secret
     * is ever included in this response.
     */
    public record RouteFinderResponse(
            String intent,
            String route,
            String label,
            String reply,
            Map<String, Object> params) {
    }

    /** Health shape required by the frontend. */
    public record AiHealthResponse(String status, String gemini) {
    }
}
