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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A trip plan is the offline-safe version of "tell someone your plan": who is
 * going, where, with which guide and emergency contact, and when the group is
 * expected to check in. Missed check-ins are what escalate a rescue.
 */
@Entity
@Table(name = "trip_plans")
@Getter
@Setter
@NoArgsConstructor
public class TripPlan extends BaseEntity {

    public enum TripStatus {
        PLANNED,
        ACTIVE,
        COMPLETED,
        /** A check-in was missed and nobody has closed the trip. */
        OVERDUE,
        CANCELLED
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private Instant plannedStartAt;

    private Instant plannedEndAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TripStatus status = TripStatus.PLANNED;

    @Column(nullable = false)
    private int groupSize = 1;

    private Integer checkInIntervalMinutes;

    @Column(length = 120)
    private String emergencyContactName;

    @Column(length = 32)
    private String emergencyContactPhone;

    private Instant lastCheckInAt;

    private Instant nextCheckInDueAt;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TripMember> members = new ArrayList<>();

    public void addMember(TripMember member) {
        member.setTrip(this);
        this.members.add(member);
    }

    public void clearMembers() {
        this.members.clear();
    }
}
