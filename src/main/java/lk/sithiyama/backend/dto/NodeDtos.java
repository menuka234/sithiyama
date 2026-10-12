package lk.sithiyama.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Companion Node registry payloads. */
public final class NodeDtos {

    private NodeDtos() {
    }

    public record NodeRequest(
            @NotBlank @Size(max = 64)
            String deviceCode,

            @Size(max = 120)
            String label,

            @Size(max = 32)
            String firmwareVersion) {
    }

    public record NodeHeartbeatRequest(
            @Min(0) @Max(100)
            Integer batteryPercent,

            Double latitude,

            Double longitude,

            @Size(max = 32)
            String firmwareVersion) {
    }

    public record NodeResponse(
            UUID id,
            UUID ownerId,
            String deviceCode,
            String label,
            String firmwareVersion,
            Integer batteryPercent,
            Instant lastSeenAt,
            Double lastLatitude,
            Double lastLongitude,
            Instant createdAt,
            Instant updatedAt) {
    }
}
