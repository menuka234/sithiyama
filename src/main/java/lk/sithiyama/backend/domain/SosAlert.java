package lk.sithiyama.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A distress alert. Mirrors the 22-byte LoRa packet sent by a Companion Node
 * (position + battery level) plus the trail walked, so a Basecamp station and
 * the cloud backend agree on what happened.
 */
@Entity
@Table(name = "sos_alerts")
@Getter
@Setter
@NoArgsConstructor
public class SosAlert extends BaseEntity {

    public enum SosStatus {
        ACTIVE,
        ACKNOWLEDGED,
        RESOLVED,
        FALSE_ALARM
    }

    public enum SosSource {
        /** Triggered from the Android app. */
        APP,
        /** Triggered by a Companion Node button or fall detector. */
        NODE,
        /** Relayed by a Basecamp station. */
        BASECAMP,
        /** Entered by an operator after a phone call. */
        MANUAL
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private TripPlan trip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SosSource source = SosSource.APP;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SosStatus status = SosStatus.ACTIVE;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    private Integer batteryPercent;

    /** What the person in trouble said, captured when the alert was raised. */
    @Column(length = 500)
    private String message;

    /** What the operations desk recorded when triaging or closing the alert. */
    @Column(length = 500)
    private String resolutionNote;

    /** WKT of the trail walked up to the alert, replayed on the Basecamp map. */
    @Column(columnDefinition = "text")
    private String trailWkt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by_id")
    private User acknowledgedBy;

    private Instant acknowledgedAt;

    private Instant resolvedAt;
}
