package lk.sithiyama.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single breadcrumb in a {@link TrackRecording}. Kept as its own row because
 * the trail is the evidence a rescue team replays, and it can be filtered or
 * thinned by time.
 */
@Entity
@Table(name = "track_points")
@Getter
@Setter
@NoArgsConstructor
public class TrackPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recording_id", nullable = false)
    private TrackRecording recording;

    @Column(nullable = false)
    private int sequenceNumber;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    private Double elevationMeters;

    private Double accuracyMeters;

    private Double speedMetersPerSecond;

    private Integer batteryPercent;

    @Column(nullable = false)
    private Instant recordedAt;
}
