package lk.sithiyama.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.sithiyama.backend.domain.NodeDevice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeDeviceRepository extends JpaRepository<NodeDevice, UUID> {

    List<NodeDevice> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<NodeDevice> findByDeviceCodeIgnoreCase(String deviceCode);

    Optional<NodeDevice> findByIdAndOwnerId(UUID id, UUID ownerId);
}
