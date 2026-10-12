package lk.sithiyama.backend.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lk.sithiyama.backend.domain.SosAlert;
import lk.sithiyama.backend.dto.CommonDtos.PageResponse;
import lk.sithiyama.backend.dto.SosDtos.SosRequest;
import lk.sithiyama.backend.dto.SosDtos.SosResponse;
import lk.sithiyama.backend.dto.SosDtos.SosStatusRequest;
import lk.sithiyama.backend.service.SosService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sos")
@Tag(name = "SOS alerts", description = "Raise and triage distress alerts")
public class SosController {

    private final SosService sosService;

    public SosController(SosService sosService) {
        this.sosService = sosService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Raise an SOS alert (app, Companion Node or Basecamp relay)")
    public SosResponse raise(@Valid @RequestBody SosRequest request) {
        return sosService.raise(request);
    }

    @GetMapping
    @Operation(summary = "List alerts: operators see everything, others see their own")
    public PageResponse<SosResponse> list(
            @RequestParam(required = false) SosAlert.SosStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return sosService.list(status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one alert, including the trail walked")
    public SosResponse get(@PathVariable UUID id) {
        return sosService.get(id);
    }

    @PostMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge an alert (ranger or admin)")
    public SosResponse acknowledge(@PathVariable UUID id) {
        return sosService.acknowledge(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Resolve, reopen or mark an alert as a false alarm (ranger or admin)")
    public SosResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody SosStatusRequest request) {
        return sosService.changeStatus(id, request);
    }
}
