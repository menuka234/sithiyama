package lk.sithiyama.backend.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.sithiyama.backend.domain.TripPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripPlanRepository extends JpaRepository<TripPlan, UUID> {

    Page<TripPlan> findByOrganizerId(UUID organizerId, Pageable pageable);

    Optional<TripPlan> findByIdAndOrganizerId(UUID id, UUID organizerId);

    List<TripPlan> findByStatusInAndNextCheckInDueAtBefore(Collection<TripPlan.TripStatus> statuses, Instant cutoff);
}
