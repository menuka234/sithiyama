package lk.sithiyama.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A person on a trip. Members may or may not be registered app users, because
 * hiking groups routinely include first-timers with no account.
 */
@Entity
@Table(name = "trip_members")
@Getter
@Setter
@NoArgsConstructor
public class TripMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripPlan trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 32)
    private String phone;

    @Column(nullable = false)
    private boolean teamLead;
}
