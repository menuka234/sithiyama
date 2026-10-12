package lk.sithiyama.backend.repository;

import java.util.UUID;
import lk.sithiyama.backend.domain.CheckIn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

    Page<CheckIn> findByTripId(UUID tripId, Pageable pageable);

    long countByTripId(UUID tripId);
}
