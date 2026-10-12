package lk.sithiyama.backend.repository;

import java.util.UUID;
import lk.sithiyama.backend.domain.SosAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SosAlertRepository extends JpaRepository<SosAlert, UUID> {

    Page<SosAlert> findByStatus(SosAlert.SosStatus status, Pageable pageable);

    Page<SosAlert> findByUserId(UUID userId, Pageable pageable);

    Page<SosAlert> findByUserIdAndStatus(UUID userId, SosAlert.SosStatus status, Pageable pageable);

    long countByStatus(SosAlert.SosStatus status);
}
