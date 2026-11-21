package lk.sithiyama.backend.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lk.sithiyama.backend.dto.AiDtos.AiStatusResponse;
import lk.sithiyama.backend.dto.AiDtos.RouteAnswerResponse;
import lk.sithiyama.backend.dto.AiDtos.RouteQuestionRequest;
import lk.sithiyama.backend.service.GeminiRouteAssistantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI route assistant", description = "Ask Gemini questions about a saved route")
public class AiController {

    private final GeminiRouteAssistantService assistant;

    public AiController(GeminiRouteAssistantService assistant) {
        this.assistant = assistant;
    }

    @GetMapping("/status")
    @Operation(summary = "Whether the Gemini assistant is configured, and which model it uses")
    public AiStatusResponse status() {
        return new AiStatusResponse(assistant.isEnabled(), assistant.model());
    }

    @PostMapping("/routes/{routeId}/ask")
    @Operation(summary = "Ask a question about one route (Gemini, with route-data fallback)")
    public RouteAnswerResponse ask(@PathVariable UUID routeId,
                                   @Valid @RequestBody RouteQuestionRequest request) {
        return assistant.ask(routeId, request);
    }
}
