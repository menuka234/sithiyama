package lk.sithiyama.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A named point of interest on a route: a trailhead, checkpoint, water source,
 * hazard, viewpoint and so on.
 */
@Entity
@Table(name = "route_waypoints")
@Getter
@Setter
@NoArgsConstructor
public class RouteWaypoint extends BaseEntity {

    public enum WaypointType {
        TRAILHEAD,
        CHECKPOINT,
        VIEWPOINT,
        WATER,
        CAMP,
        SHELTER,
        HAZARD,
        EXIT
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(nullable = false)
    private int sequenceNumber;

    @Column(length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WaypointType type = WaypointType.CHECKPOINT;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    private Double elevationMeters;

    @Column(length = 500)
    private String note;

    /**
     * Optional checkpoint photo, stored as a data URL / base64 string so it
     * works with both PostgreSQL (text) and MySQL (longtext). Kept short by
     * downscaling images in the client before upload.
     */
    @Column(columnDefinition = "text")
    private String photo;
}
