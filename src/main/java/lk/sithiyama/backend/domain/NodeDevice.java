package lk.sithiyama.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A registered Companion Node (RAK4631-class LoRa tracker). Lets a group see
 * which node is in the field, its firmware and its last known battery/position.
 */
@Entity
@Table(name = "node_devices")
@Getter
@Setter
@NoArgsConstructor
public class NodeDevice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, unique = true, length = 64)
    private String deviceCode;

    @Column(length = 120)
    private String label;

    @Column(length = 32)
    private String firmwareVersion;

    private Integer batteryPercent;

    private Instant lastSeenAt;

    private Double lastLatitude;

    private Double lastLongitude;
}
