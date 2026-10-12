package lk.sithiyama.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lk.sithiyama.backend.domain.GeoPoint;
import lk.sithiyama.backend.domain.Route;
import lk.sithiyama.backend.domain.RouteWaypoint;

/** Route library payloads. */
public final class RouteDtos {

    private RouteDtos() {
    }

    public record WaypointRequest(
            Integer sequenceNumber,

            @Size(max = 160)
            String name,

            RouteWaypoint.WaypointType type,

            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
            Double latitude,

            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
            Double longitude,

            Double elevationMeters,

            @Size(max = 500)
            String note) {
    }

    public record RouteRequest(
            @NotBlank @Size(max = 160)
            String name,

            @Size(max = 4000)
            String description,

            @Size(max = 120)
            String region,

            Route.Difficulty difficulty,

            Route.Visibility visibility,

            @Min(0)
            Double distanceMeters,

            @Min(0)
            Double ascentMeters,

            @Min(0)
            Integer estimatedDurationMinutes,

            Set<@Size(max = 48) String> tags,

            /** The polyline: at least two points, in walk order. */
            @NotNull @Size(min = 2, message = "a route path needs at least two points")
            List<GeoPoint> path,

            List<WaypointRequest> waypoints) {
    }

    public record WaypointResponse(
            UUID id,
            int sequenceNumber,
            String name,
            RouteWaypoint.WaypointType type,
            double latitude,
            double longitude,
            Double elevationMeters,
            String note) {
    }

    public record RouteResponse(
            UUID id,
            String name,
            String description,
            String region,
            Route.Difficulty difficulty,
            Route.Visibility visibility,
            double distanceMeters,
            double ascentMeters,
            int estimatedDurationMinutes,
            Double startLatitude,
            Double startLongitude,
            Double endLatitude,
            Double endLongitude,
            Set<String> tags,
            List<GeoPoint> path,
            List<WaypointResponse> waypoints,
            UUID creatorId,
            String creatorName,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** Lighter shape for list responses (no polyline payload). */
    public record RouteSummary(
            UUID id,
            String name,
            String description,
            String region,
            Route.Difficulty difficulty,
            Route.Visibility visibility,
            double distanceMeters,
            double ascentMeters,
            int estimatedDurationMinutes,
            Double startLatitude,
            Double startLongitude,
            Double endLatitude,
            Double endLongitude,
            Set<String> tags,
            UUID creatorId,
            String creatorName,
            Instant createdAt,
            Instant updatedAt) {
    }

    /**
     * Optional filters for the library search. {@code nearLatitude}/{@code nearLongitude}
     * with {@code radiusMeters} switch the query to a radius search.
     */
    public record RouteSearchCriteria(
            String q,
            String region,
            Route.Difficulty difficulty,
            Double minDistanceMeters,
            Double maxDistanceMeters,
            Double nearLatitude,
            Double nearLongitude,
            Double radiusMeters,
            boolean mineOnly) {
    }
}
