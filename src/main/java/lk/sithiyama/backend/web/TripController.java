package lk.sithiyama.backend.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lk.sithiyama.backend.domain.TripPlan;
import lk.sithiyama.backend.dto.CommonDtos.PageResponse;
import lk.sithiyama.backend.dto.TripDtos.CheckInRequest;
import lk.sithiyama.backend.dto.TripDtos.CheckInResponse;
import lk.sithiyama.backend.dto.TripDtos.TripRequest;
import lk.sithiyama.backend.dto.TripDtos.TripResponse;
import lk.sithiyama.backend.service.TripService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips")
@Validated
@Tag(name = "Trips and check-ins", description = "Trip plans, proof-of-life check-ins and overdue trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a trip plan before heading out")
    public TripResponse create(@Valid @RequestBody TripRequest request) {
        return tripService.create(request);
    }

    @GetMapping
    @Operation(summary = "List the trips you organise")
    public PageResponse<TripResponse> listMine(
            @PageableDefault(size = 20, sort = "plannedStartAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return tripService.listMine(pageable);
    }

    @GetMapping("/overdue")
    @Operation(summary = "Trips that have missed their check-in window")
    public List<TripResponse> overdue() {
        return tripService.overdue();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one trip plan")
    public TripResponse get(@PathVariable UUID id) {
        return tripService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a trip plan")
    public TripResponse update(@PathVariable UUID id, @Valid @RequestBody TripRequest request) {
        return tripService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move a trip between PLANNED, ACTIVE, COMPLETED, OVERDUE and CANCELLED")
    public TripResponse changeStatus(@PathVariable UUID id, @RequestParam TripPlan.TripStatus status) {
        return tripService.changeStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a trip plan")
    public void delete(@PathVariable UUID id) {
        tripService.delete(id);
    }

    @PostMapping("/{id}/check-ins")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a check-in and re-arm the next deadline")
    public CheckInResponse checkIn(@PathVariable UUID id, @Valid @RequestBody CheckInRequest request) {
        return tripService.checkIn(id, request);
    }

    @GetMapping("/{id}/check-ins")
    @Operation(summary = "List the check-ins recorded for a trip")
    public PageResponse<CheckInResponse> listCheckIns(
            @PathVariable UUID id,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return tripService.listCheckIns(id, pageable);
    }
}
