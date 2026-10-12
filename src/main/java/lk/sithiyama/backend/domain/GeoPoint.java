package lk.sithiyama.backend.domain;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * A WGS84 (EPSG:4326) coordinate. Shared by API DTOs and the geo utilities so
 * paths are described by one type everywhere.
 */
public record GeoPoint(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {
}
