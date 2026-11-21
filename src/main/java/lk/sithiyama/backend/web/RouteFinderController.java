package lk.sithiyama.backend.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.sithiyama.backend.config.GeminiConfig;
import lk.sithiyama.backend.dto.AiDtos.AiHealthResponse;
import lk.sithiyama.backend.dto.AiDtos.FindRouteRequest;
import lk.sithiyama.backend.dto.AiDtos.RouteFinderResponse;
import lk.sithiyama.backend.service.RouteFinderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mental-health route finder. Public endpoints: the only credential is the
 * optional x-gemini-key header (server .env key is the fallback).
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI route finder", description = "Maps Sinhala/Singlish/English feelings to Sithiyama routes")
public class RouteFinderController {

    private final RouteFinderService routeFinder;

    public RouteFinderController(RouteFinderService routeFinder) {
        this.routeFinder = routeFinder;
    }

    @GetMapping("/health")
    @Operation(summary = "Health check; reports whether a Gemini key is available")
    public AiHealthResponse health(
            @RequestHeader(value = GeminiConfig.API_KEY_HEADER, required = false) String geminiKey) {
        return routeFinder.health(geminiKey);
    }

    @PostMapping("/find-route")
    @Operation(summary = "Return JSON-only route suggestion for a mental-health message")
    public RouteFinderResponse findRoute(
            @RequestHeader(value = GeminiConfig.API_KEY_HEADER, required = false) String geminiKey,
            @Valid @RequestBody FindRouteRequest request) {
        return routeFinder.findRoute(request.message(), geminiKey);
    }
}
