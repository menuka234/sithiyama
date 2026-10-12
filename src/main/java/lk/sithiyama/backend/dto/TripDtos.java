package lk.sithiyama.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.sithiyama.backend.domain.CheckIn;
import lk.sithiyama.backend.domain.TripPlan;

/** Trip plan and check-in payloads. */
public final class TripDtos {

    private TripDtos() {
    }

    public record TripMemberRequest(
            @NotBlank @Size(max = 120)
            String name,

            @Size(max = 32)
            String phone,

            UUID userId,

            Boolean teamLead) {
    }

    public record TripRequest(
            @NotBlank @Size(max = 160)
            String title,

            @Size(max = 4000)
            String notes,

            UUID routeId,

            @NotNull
            Instant plannedStartAt,

            Instant plannedEndAt,

            @Min(1) @Max(200)
            Integer groupSize,

            @Min(5) @Max(1440)
            Integer checkInIntervalMinutes,

            @Size(max = 120)
            String emergencyContactName,

            @Size(max = 32)
            String emergencyContactPhone,

            TripPlan.TripStatus status,

            List<TripMemberRequest> members) {
    }

    public record TripMemberResponse(
            UUID id,
            String name,
            String phone,
            UUID userId,
            boolean teamLead) {
    }

    public record TripResponse(
            UUID id,
            String title,
            String notes,
            UUID routeId,
            String routeName,
            UUID organizerId,
            String organizerName,
            TripPlan.TripStatus status,
            int groupSize,
            Integer checkInIntervalMinutes,
            Instant plannedStartAt,
            Instant plannedEndAt,
            String emergencyContactName,
            String emergencyContactPhone,
            Instant lastCheckInAt,
            Instant nextCheckInDueAt,
            List<TripMemberResponse> members,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CheckInRequest(
            Double latitude,
            Double longitude,

            @Min(0) @Max(100)
            Integer batteryPercent,

            @Size(max = 500)
            String message,

            CheckIn.CheckInSource source) {
    }

    public record CheckInResponse(
            UUID id,
            UUID tripId,
            UUID userId,
            CheckIn.CheckInSource source,
            Double latitude,
            Double longitude,
            Integer batteryPercent,
            String message,
            Instant createdAt) {
    }
}
