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
 * A proof-of-life ping for a {@link TripPlan}. Check-ins arrive from the app,
 * from a Companion Node over Bluetooth, or from a Basecamp station.
 */
@Entity
@Table(name = "check_ins")
@Getter
@Setter
@NoArgsConstructor
public class CheckIn extends BaseEntity {

    public enum CheckInSource {
        APP,
        NODE,
        BASECAMP
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripPlan trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CheckInSource source = CheckInSource.APP;

    private Double latitude;

    private Double longitude;

    private Integer batteryPercent;

    @Column(length = 500)
    private String message;
}
