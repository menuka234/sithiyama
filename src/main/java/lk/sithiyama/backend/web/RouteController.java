package lk.sithiyama.backend.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import java.util.UUID;
import lk.sithiyama.backend.domain.Route;
import lk.sithiyama.backend.dto.CommonDtos.PageResponse;
import lk.sithiyama.backend.dto.RouteDtos.RouteRequest;
import lk.sithiyama.backend.dto.RouteDtos.RouteResponse;
import lk.sithiyama.backend.dto.RouteDtos.RouteSearchCriteria;
import lk.sithiyama.backend.dto.RouteDtos.RouteSummary;
import lk.sithiyama.backend.service.RouteService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
@Validated
@Tag(name = "Route library", description = "The shared, offline-first trail library")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping
    @Operation(summary = "Search the library (text, region, difficulty, distance and radius filters)")
    public PageResponse<RouteSummary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Route.Difficulty difficulty,
            @RequestParam(required = false) @DecimalMin("0") Double minDistanceMeters,
            @RequestParam(required = false) @DecimalMin("0") Double maxDistanceMeters,
            @RequestParam(required = false) Double nearLat,
            @RequestParam(required = false) Double nearLon,
            @RequestParam(required = false) @DecimalMin("1") Double radiusMeters,
            @RequestParam(defaultValue = "false") boolean mine,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        RouteSearchCriteria criteria = new RouteSearchCriteria(
                q, region, difficulty, minDistanceMeters, maxDistanceMeters,
                nearLat, nearLon, radiusMeters, mine);
        return routeService.search(criteria, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one route, including its polyline and waypoints")
    public RouteResponse get(@PathVariable UUID id) {
        return routeService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publish a route to the library")
    public RouteResponse create(@Valid @RequestBody RouteRequest request) {
        return routeService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a route you created")
    public RouteResponse update(@PathVariable UUID id, @Valid @RequestBody RouteRequest request) {
        return routeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a route you created")
    public void delete(@PathVariable UUID id) {
        routeService.delete(id);
    }
}
