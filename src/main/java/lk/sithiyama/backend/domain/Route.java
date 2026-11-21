package lk.sithiyama.backend.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A trail in the community route library. The polyline is stored as WKT text
 * ({@code path_wkt}) with a plain bounding box for quick filtering; PostGIS
 * mirrors it into an indexed {@code geometry(LineString,4326)} column.
 */
@Entity
@Table(name = "routes")
@Getter
@Setter
@NoArgsConstructor
public class Route extends BaseEntity {

    public enum Difficulty {
        EASY,
        MODERATE,
        HARD,
        EXPERT
    }

    public enum Visibility {
        /** Listed in the public library. */
        PUBLIC,
        /** Reachable by link, hidden from listings. */
        UNLISTED,
        /** Only the creator (and admins) can read it. */
        PRIVATE,
        /** Visible only to friends of the creator. */
        FRIENDS
    }

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 120)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Difficulty difficulty = Difficulty.MODERATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Visibility visibility = Visibility.PUBLIC;

    @Column(nullable = false)
    private double distanceMeters;

    @Column(nullable = false)
    private double ascentMeters;

    @Column(nullable = false)
    private int estimatedDurationMinutes;

    @Column(nullable = false)
    private double startLatitude;

    @Column(nullable = false)
    private double startLongitude;

    private Double endLatitude;

    private Double endLongitude;

    @Column(nullable = false)
    private double minLatitude;

    @Column(nullable = false)
    private double minLongitude;

    @Column(nullable = false)
    private double maxLatitude;

    @Column(nullable = false)
    private double maxLongitude;

    @Column(nullable = false, columnDefinition = "text")
    private String pathWkt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "route_tags", joinColumns = @JoinColumn(name = "route_id"))
    @Column(name = "tag", length = 48)
    private Set<String> tags = new LinkedHashSet<>();

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequenceNumber asc")
    private List<RouteWaypoint> waypoints = new ArrayList<>();

    public void addWaypoint(RouteWaypoint waypoint) {
        waypoint.setRoute(this);
        this.waypoints.add(waypoint);
    }

    public void clearWaypoints() {
        this.waypoints.clear();
    }
}
