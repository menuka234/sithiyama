package lk.sithiyama.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A registered SITHIYAMA user. Roles drive access to the route library, trip
 * plans and the SOS console.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    public enum Role {
        ROLE_HIKER,
        ROLE_GUIDE,
        ROLE_RANGER,
        ROLE_ADMIN
    }

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(length = 32)
    private String phone;

    @Column(nullable = false, length = 16)
    private String locale = "en";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role = Role.ROLE_HIKER;

    @Column(nullable = false)
    private boolean enabled = true;

    public boolean isAdmin() {
        return role == Role.ROLE_ADMIN;
    }

    public boolean isRangerOrAdmin() {
        return role == Role.ROLE_RANGER || role == Role.ROLE_ADMIN;
    }
}
