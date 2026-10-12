package lk.sithiyama.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.sithiyama.backend.domain.GeoPoint;
import lk.sithiyama.backend.domain.SosAlert;

/** SOS alert payloads. */
public final class SosDtos {

    private SosDtos() {
    }

    public record SosRequest(
            UUID tripId,

            SosAlert.SosSource source,

            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
            Double latitude,

            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
            Double longitude,

            @Min(0) @Max(100)
            Integer batteryPercent,

            @Size(max = 500)
            String message,

            /** The trail walked up to the alert, replayed on the Basecamp map. */
            List<GeoPoint> trail) {
    }

    public record SosStatusRequest(
            @NotNull
            SosAlert.SosStatus status,

            @Size(max = 500)
            String note) {
    }

    public record SosResponse(
            UUID id,
            UUID userId,
            String userName,
            UUID tripId,
            String tripTitle,
            SosAlert.SosSource source,
            SosAlert.SosStatus status,
            double latitude,
            double longitude,
            Integer batteryPercent,
            String message,
            UUID acknowledgedById,
            String acknowledgedByName,
            Instant acknowledgedAt,
            Instant resolvedAt,
            List<GeoPoint> trail,
            Instant createdAt,
            Instant updatedAt) {
    }
}
