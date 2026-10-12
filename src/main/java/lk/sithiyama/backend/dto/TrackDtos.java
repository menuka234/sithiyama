package lk.sithiyama.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.sithiyama.backend.domain.GeoPoint;
import lk.sithiyama.backend.domain.TrackRecording;

/** Recorded-track upload and read payloads. */
public final class TrackDtos {

    private TrackDtos() {
    }

    public record TrackPointRequest(
            Integer sequenceNumber,

            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
            Double latitude,

            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
            Double longitude,

            Double elevationMeters,

            Double accuracyMeters,

            Double speedMetersPerSecond,

            @Min(0) @Max(100)
            Integer batteryPercent,

            Instant recordedAt) {
    }

    public record TrackUploadRequest(
            @NotBlank @Size(max = 160)
            String name,

            UUID routeId,

            TrackRecording.TrackStatus status,

            TrackRecording.RecordingSource source,

            Instant startedAt,

            Instant endedAt,

            @NotNull @Size(min = 1, message = "a track needs at least one point")
            List<TrackPointRequest> points) {
    }

    public record TrackPointResponse(
            UUID id,
            int sequenceNumber,
            double latitude,
            double longitude,
            Double elevationMeters,
            Double accuracyMeters,
            Double speedMetersPerSecond,
            Integer batteryPercent,
            Instant recordedAt) {
    }

    public record TrackResponse(
            UUID id,
            String name,
            UUID routeId,
            String routeName,
            UUID userId,
            TrackRecording.TrackStatus status,
            TrackRecording.RecordingSource source,
            Instant startedAt,
            Instant endedAt,
            double distanceMeters,
            int pointCount,
            List<GeoPoint> path,
            List<TrackPointResponse> points,
            Instant createdAt) {
    }

    public record TrackSummary(
            UUID id,
            String name,
            UUID routeId,
            String routeName,
            UUID userId,
            TrackRecording.TrackStatus status,
            TrackRecording.RecordingSource source,
            Instant startedAt,
            Instant endedAt,
            double distanceMeters,
            int pointCount,
            Instant createdAt) {
    }
}
