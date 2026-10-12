package lk.sithiyama.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.sithiyama.backend.domain.NodeDevice;
import lk.sithiyama.backend.domain.User;
import lk.sithiyama.backend.dto.NodeDtos.NodeHeartbeatRequest;
import lk.sithiyama.backend.dto.NodeDtos.NodeRequest;
import lk.sithiyama.backend.dto.NodeDtos.NodeResponse;
import lk.sithiyama.backend.exception.DuplicateResourceException;
import lk.sithiyama.backend.exception.ResourceNotFoundException;
import lk.sithiyama.backend.repository.NodeDeviceRepository;
import lk.sithiyama.backend.repository.UserRepository;
import lk.sithiyama.backend.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registry and heartbeat for the pocket-sized Companion Nodes. */
@Service
public class NodeService {

    private final NodeDeviceRepository nodeRepository;
    private final UserRepository userRepository;

    public NodeService(NodeDeviceRepository nodeRepository, UserRepository userRepository) {
        this.nodeRepository = nodeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public NodeResponse register(NodeRequest request) {
        User owner = currentUser();
        String code = request.deviceCode().trim();

        NodeDevice existing = nodeRepository.findByDeviceCodeIgnoreCase(code).orElse(null);
        if (existing != null) {
            if (existing.getOwner().getId().equals(owner.getId())) {
                return toResponse(existing); // idempotent re-registration
            }
            throw new DuplicateResourceException("Node '" + code + "' is already registered to another account");
        }

        NodeDevice node = new NodeDevice();
        node.setOwner(owner);
        node.setDeviceCode(code);
        node.setLabel(request.label());
        node.setFirmwareVersion(request.firmwareVersion());
        return toResponse(nodeRepository.save(node));
    }

    @Transactional(readOnly = true)
    public List<NodeResponse> listMine() {
        return nodeRepository.findByOwnerIdOrderByCreatedAtDesc(SecurityUtils.requireUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NodeResponse heartbeat(UUID id, NodeHeartbeatRequest request) {
        NodeDevice node = nodeRepository.findByIdAndOwnerId(id, SecurityUtils.requireUserId())
                .orElseThrow(() -> ResourceNotFoundException.of("Node", id));
        node.setLastSeenAt(Instant.now());
        if (request.batteryPercent() != null) {
            node.setBatteryPercent(request.batteryPercent());
        }
        if (request.firmwareVersion() != null && !request.firmwareVersion().isBlank()) {
            node.setFirmwareVersion(request.firmwareVersion());
        }
        if (request.latitude() != null && request.longitude() != null) {
            node.setLastLatitude(request.latitude());
            node.setLastLongitude(request.longitude());
        }
        return toResponse(nodeRepository.save(node));
    }

    private User currentUser() {
        return userRepository.findById(SecurityUtils.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Account no longer exists"));
    }

    private NodeResponse toResponse(NodeDevice node) {
        return new NodeResponse(
                node.getId(),
                node.getOwner().getId(),
                node.getDeviceCode(),
                node.getLabel(),
                node.getFirmwareVersion(),
                node.getBatteryPercent(),
                node.getLastSeenAt(),
                node.getLastLatitude(),
                node.getLastLongitude(),
                node.getCreatedAt(),
                node.getUpdatedAt());
    }
}
