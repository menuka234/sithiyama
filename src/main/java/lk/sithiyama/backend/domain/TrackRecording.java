package lk.sithiyama.backend.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A GPS track uploaded from the Android app or synced from a Companion Node's
 * "black box" flash log over Bluetooth. This is what the Basecamp dashboard
 * replays when an SOS is raised ("the trail the person walked").
 */
@Entity
@Table(name = "track_recordings")
@Getter
@Setter
@NoArgsConstructor
public class TrackRecording extends BaseEntity {

    public enum TrackStatus {
        RECORDING,
        COMPLETED,
        ABORTED
    }

    public enum RecordingSource {
        /** Recorded on the phone (Flutter app). */
        APP,
        /** Breadcrumbs synced from a Companion Node. */
        NODE,
        /** Injected from a Basecamp station. */
        BASECAMP
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TrackStatus status = TrackStatus.COMPLETED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RecordingSource source = RecordingSource.APP;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant endedAt;

    @Column(nullable = false)
    private double distanceMeters;

    @Column(nullable = false)
    private int pointCount;

    private Double minLatitude;

    private Double minLongitude;

    private Double maxLatitude;

    private Double maxLongitude;

    @Column(columnDefinition = "text")
    private String pathWkt;

    @OneToMany(mappedBy = "recording", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequenceNumber asc")
    private List<TrackPoint> points = new ArrayList<>();

    public void addPoint(TrackPoint point) {
        point.setRecording(this);
        this.points.add(point);
    }
}
