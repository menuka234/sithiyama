package lk.sithiyama.backend.service;

import java.time.Instant;
import java.util.UUID;
import lk.sithiyama.backend.domain.SosAlert;
import lk.sithiyama.backend.domain.User;
import lk.sithiyama.backend.dto.CommonDtos.PageResponse;
import lk.sithiyama.backend.dto.SosDtos.SosRequest;
import lk.sithiyama.backend.dto.SosDtos.SosResponse;
import lk.sithiyama.backend.dto.SosDtos.SosStatusRequest;
import lk.sithiyama.backend.exception.ForbiddenException;
import lk.sithiyama.backend.exception.ResourceNotFoundException;
import lk.sithiyama.backend.repository.SosAlertRepository;
import lk.sithiyama.backend.repository.TripPlanRepository;
import lk.sithiyama.backend.repository.UserRepository;
import lk.sithiyama.backend.security.AppUserPrincipal;
import lk.sithiyama.backend.security.SecurityUtils;
import lk.sithiyama.backend.util.GeoUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Distress alerts and their triage workflow: ACTIVE -> ACKNOWLEDGED -> RESOLVED
 * (or FALSE_ALARM). Rangers and admins act as the operations desk.
 */
@Service
public class SosService {

    private final SosAlertRepository sosRepository;
    private final TripPlanRepository tripRepository;
    private final UserRepository userRepository;

    public SosService(SosAlertRepository sosRepository,
                      TripPlanRepository tripRepository,
                      UserRepository userRepository) {
        this.sosRepository = sosRepository;
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public SosResponse raise(SosRequest request) {
        SosAlert alert = new SosAlert();
        alert.setUser(currentUser());
        if (request.tripId() != null) {
            alert.setTrip(tripRepository.findById(request.tripId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Trip", request.tripId())));
        }
        alert.setSource(request.source() == null ? SosAlert.SosSource.APP : request.source());
        alert.setStatus(SosAlert.SosStatus.ACTIVE);
        alert.setLatitude(request.latitude());
        alert.setLongitude(request.longitude());
        alert.setBatteryPercent(request.batteryPercent());
        alert.setMessage(request.message());
        if (request.trail() != null && request.trail().size() >= 2) {
            alert.setTrailWkt(GeoUtil.toLineStringWkt(request.trail()));
        }
        return toResponse(sosRepository.save(alert));
    }

    /**
     * Operators see every alert; everyone else sees only their own, because an
     * alert carries a live location.
     */
    @Transactional(readOnly = true)
    public PageResponse<SosResponse> list(SosAlert.SosStatus status, Pageable pageable) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Page<SosAlert> page;
        if (isOperator(principal)) {
            page = status == null
                    ? sosRepository.findAll(pageable)
                    : sosRepository.findByStatus(status, pageable);
        } else {
            page = status == null
                    ? sosRepository.findByUserId(principal.id(), pageable)
                    : sosRepository.findByUserIdAndStatus(principal.id(), status, pageable);
        }
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public SosResponse get(UUID id) {
        SosAlert alert = sosRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("SOS alert", id));
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        boolean visible = isOperator(principal)
                || (alert.getUser() != null && alert.getUser().getId().equals(principal.id()));
        if (!visible) {
            throw ResourceNotFoundException.of("SOS alert", id);
        }
        return toResponse(alert);
    }

    /** First responder takes ownership of the alert. */
    @Transactional
    public SosResponse acknowledge(UUID id) {
        requireOperator();
        SosAlert alert = requireAlert(id);
        if (alert.getStatus() == SosAlert.SosStatus.ACTIVE) {
            alert.setStatus(SosAlert.SosStatus.ACKNOWLEDGED);
            alert.setAcknowledgedBy(currentUser());
            alert.setAcknowledgedAt(Instant.now());
        }
        return toResponse(sosRepository.save(alert));
    }

    /** Move an alert to any terminal or intermediate state. */
    @Transactional
    public SosResponse changeStatus(UUID id, SosStatusRequest request) {
        requireOperator();
        if (request.status() == SosAlert.SosStatus.ACKNOWLEDGED) {
            return acknowledge(id);
        }
        SosAlert alert = requireAlert(id);
        alert.setStatus(request.status());
        if (request.status() == SosAlert.SosStatus.RESOLVED
                || request.status() == SosAlert.SosStatus.FALSE_ALARM) {
            alert.setResolvedAt(Instant.now());
        }
        if (alert.getAcknowledgedBy() == null) {
            alert.setAcknowledgedBy(currentUser());
            alert.setAcknowledgedAt(Instant.now());
        }
        if (request.note() != null && !request.note().isBlank()) {
            alert.setResolutionNote(request.note());
        }
        return toResponse(sosRepository.save(alert));
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private boolean isOperator(AppUserPrincipal principal) {
        return principal.role() == User.Role.ROLE_RANGER || principal.role() == User.Role.ROLE_ADMIN;
    }

    private void requireOperator() {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        if (!isOperator(principal)) {
            throw new ForbiddenException("Only a ranger or admin can triage SOS alerts");
        }
    }

    private SosAlert requireAlert(UUID id) {
        return sosRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("SOS alert", id));
    }

    private User currentUser() {
        return userRepository.findById(SecurityUtils.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Account no longer exists"));
    }

    private SosResponse toResponse(SosAlert alert) {
        return new SosResponse(
                alert.getId(),
                alert.getUser() == null ? null : alert.getUser().getId(),
                alert.getUser() == null ? null : alert.getUser().getDisplayName(),
                alert.getTrip() == null ? null : alert.getTrip().getId(),
                alert.getTrip() == null ? null : alert.getTrip().getTitle(),
                alert.getSource(),
                alert.getStatus(),
                alert.getLatitude(),
                alert.getLongitude(),
                alert.getBatteryPercent(),
                alert.getMessage(),
                alert.getResolutionNote(),
                alert.getAcknowledgedBy() == null ? null : alert.getAcknowledgedBy().getId(),
                alert.getAcknowledgedBy() == null ? null : alert.getAcknowledgedBy().getDisplayName(),
                alert.getAcknowledgedAt(),
                alert.getResolvedAt(),
                GeoUtil.parseLineStringWkt(alert.getTrailWkt()),
                alert.getCreatedAt(),
                alert.getUpdatedAt());
    }
}
