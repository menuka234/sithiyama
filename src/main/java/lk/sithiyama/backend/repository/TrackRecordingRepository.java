package lk.sithiyama.backend.repository;

import java.util.Optional;
import java.util.UUID;
import lk.sithiyama.backend.domain.TrackRecording;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackRecordingRepository extends JpaRepository<TrackRecording, UUID> {

    Page<TrackRecording> findByUserId(UUID userId, Pageable pageable);

    Optional<TrackRecording> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    long countByRouteId(UUID routeId);
}
