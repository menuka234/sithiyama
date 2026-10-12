package lk.sithiyama.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.sithiyama.backend.domain.CheckIn;
import lk.sithiyama.backend.domain.TripMember;
import lk.sithiyama.backend.domain.TripPlan;
import lk.sithiyama.backend.domain.User;
import lk.sithiyama.backend.dto.CommonDtos.PageResponse;
import lk.sithiyama.backend.dto.TripDtos.CheckInRequest;
import lk.sithiyama.backend.dto.TripDtos.CheckInResponse;
import lk.sithiyama.backend.dto.TripDtos.TripMemberRequest;
import lk.sithiyama.backend.dto.TripDtos.TripMemberResponse;
import lk.sithiyama.backend.dto.TripDtos.TripRequest;
import lk.sithiyama.backend.dto.TripDtos.TripResponse;
import lk.sithiyama.backend.exception.BadRequestException;
import lk.sithiyama.backend.exception.ForbiddenException;
import lk.sithiyama.backend.exception.ResourceNotFoundException;
import lk.sithiyama.backend.repository.CheckInRepository;
import lk.sithiyama.backend.repository.RouteRepository;
import lk.sithiyama.backend.repository.TripPlanRepository;
import lk.sithiyama.backend.repository.UserRepository;
import lk.sithiyama.backend.security.AppUserPrincipal;
import lk.sithiyama.backend.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trip plans and check-ins: the structured version of "tell someone your plan",
 * and the trigger for escalation when a check-in is missed.
 */
@Service
public class TripService {

    private final TripPlanRepository tripRepository;
    private final CheckInRepository checkInRepository;
    private final RouteRepository routeRepository;
    private final UserRepository userRepository;

    public TripService(TripPlanRepository tripRepository,
                       CheckInRepository checkInRepository,
                       RouteRepository routeRepository,
                       UserRepository userRepository) {
        this.tripRepository = tripRepository;
        this.checkInRepository = checkInRepository;
        this.routeRepository = routeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TripResponse create(TripRequest request) {
        TripPlan trip = new TripPlan();
        apply(trip, request);
        trip.setOrganizer(currentUser());
        refreshNextCheckIn(trip);
        return toResponse(tripRepository.save(trip));
    }

    @Transactional(readOnly = true)
    public PageResponse<TripResponse> listMine(Pageable pageable) {
        Page<TripPlan> page = tripRepository.findByOrganizerId(SecurityUtils.requireUserId(), pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public TripResponse get(UUID id) {
        return toResponse(requireTrip(id));
    }

    @Transactional
    public TripResponse update(UUID id, TripRequest request) {
        TripPlan trip = requireTrip(id);
        apply(trip, request);
        refreshNextCheckIn(trip);
        return toResponse(tripRepository.save(trip));
    }

    @Transactional
    public TripResponse changeStatus(UUID id, TripPlan.TripStatus status) {
        TripPlan trip = requireTrip(id);
        if (status == null) {
            throw new BadRequestException("status is required");
        }
        trip.setStatus(status);
        return toResponse(tripRepository.save(trip));
    }

    @Transactional
    public void delete(UUID id) {
        tripRepository.delete(requireTrip(id));
    }

    @Transactional
    public CheckInResponse checkIn(UUID tripId, CheckInRequest request) {
        TripPlan trip = requireTrip(tripId);
        CheckIn checkIn = new CheckIn();
        checkIn.setTrip(trip);
        checkIn.setUser(currentUser());
        checkIn.setSource(request.source() == null ? CheckIn.CheckInSource.APP : request.source());
        checkIn.setLatitude(request.latitude());
        checkIn.setLongitude(request.longitude());
        checkIn.setBatteryPercent(request.batteryPercent());
        checkIn.setMessage(request.message());

        trip.setLastCheckInAt(Instant.now());
        if (trip.getStatus() == TripPlan.TripStatus.PLANNED
                || trip.getStatus() == TripPlan.TripStatus.OVERDUE) {
            trip.setStatus(TripPlan.TripStatus.ACTIVE);
        }
        refreshNextCheckIn(trip);

        return toCheckInResponse(checkInRepository.save(checkIn));
    }

    @Transactional(readOnly = true)
    public PageResponse<CheckInResponse> listCheckIns(UUID tripId, Pageable pageable) {
        requireTrip(tripId);
        Page<CheckIn> page = checkInRepository.findByTripId(tripId, pageable);
        return PageResponse.of(page.map(this::toCheckInResponse));
    }

    /** Active trips whose next check-in is overdue - what a ranger would watch. */
    @Transactional(readOnly = true)
    public List<TripResponse> overdue() {
        List<TripPlan> trips = tripRepository.findByStatusInAndNextCheckInDueAtBefore(
                List.of(TripPlan.TripStatus.PLANNED, TripPlan.TripStatus.ACTIVE), Instant.now());
        return trips.stream().map(this::toResponse).toList();
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private void apply(TripPlan trip, TripRequest request) {
        if (request.plannedEndAt() != null && request.plannedEndAt().isBefore(request.plannedStartAt())) {
            throw new BadRequestException("plannedEndAt must be after plannedStartAt");
        }
        trip.setTitle(request.title().trim());
        trip.setNotes(request.notes());
        trip.setPlannedStartAt(request.plannedStartAt());
        trip.setPlannedEndAt(request.plannedEndAt());
        trip.setCheckInIntervalMinutes(request.checkInIntervalMinutes());
        trip.setEmergencyContactName(request.emergencyContactName());
        trip.setEmergencyContactPhone(request.emergencyContactPhone());
        if (request.status() != null) {
            trip.setStatus(request.status());
        }

        if (request.routeId() != null) {
            trip.setRoute(routeRepository.findById(request.routeId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Route", request.routeId())));
        } else {
            trip.setRoute(null);
        }

        List<TripMemberRequest> members = request.members();
        if (members != null) {
            trip.clearMembers();
            for (TripMemberRequest member : members) {
                TripMember entity = new TripMember();
                entity.setName(member.name().trim());
                entity.setPhone(member.phone());
                entity.setTeamLead(Boolean.TRUE.equals(member.teamLead()));
                if (member.userId() != null) {
                    entity.setUser(userRepository.findById(member.userId())
                            .orElseThrow(() -> ResourceNotFoundException.of("User", member.userId())));
                }
                trip.addMember(entity);
            }
        }

        if (request.groupSize() != null) {
            trip.setGroupSize(request.groupSize());
        } else if (members != null && !members.isEmpty()) {
            trip.setGroupSize(members.size());
        }
    }

    /** Re-arm the check-in deadline whenever the interval or last check-in changes. */
    private void refreshNextCheckIn(TripPlan trip) {
        if (trip.getCheckInIntervalMinutes() == null) {
            trip.setNextCheckInDueAt(null);
            return;
        }
        Instant base = trip.getLastCheckInAt() != null ? trip.getLastCheckInAt() : Instant.now();
        trip.setNextCheckInDueAt(base.plus(Duration.ofMinutes(trip.getCheckInIntervalMinutes())));
    }

    private TripPlan requireTrip(UUID id) {
        TripPlan trip = tripRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Trip", id));
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        boolean allowed = principal.id().equals(trip.getOrganizer().getId())
                || principal.role() == User.Role.ROLE_ADMIN
                || principal.role() == User.Role.ROLE_RANGER;
        if (!allowed) {
            throw new ForbiddenException("Only the trip organiser can access this trip");
        }
        return trip;
    }

    private User currentUser() {
        return userRepository.findById(SecurityUtils.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Account no longer exists"));
    }

    private TripResponse toResponse(TripPlan trip) {
        return new TripResponse(
                trip.getId(),
                trip.getTitle(),
                trip.getNotes(),
                trip.getRoute() == null ? null : trip.getRoute().getId(),
                trip.getRoute() == null ? null : trip.getRoute().getName(),
                trip.getOrganizer().getId(),
                trip.getOrganizer().getDisplayName(),
                trip.getStatus(),
                trip.getGroupSize(),
                trip.getCheckInIntervalMinutes(),
                trip.getPlannedStartAt(),
                trip.getPlannedEndAt(),
                trip.getEmergencyContactName(),
                trip.getEmergencyContactPhone(),
                trip.getLastCheckInAt(),
                trip.getNextCheckInDueAt(),
                trip.getMembers().stream().map(this::toMemberResponse).toList(),
                trip.getCreatedAt(),
                trip.getUpdatedAt());
    }

    private TripMemberResponse toMemberResponse(TripMember member) {
        return new TripMemberResponse(
                member.getId(),
                member.getName(),
                member.getPhone(),
                member.getUser() == null ? null : member.getUser().getId(),
                member.isTeamLead());
    }

    private CheckInResponse toCheckInResponse(CheckIn checkIn) {
        return new CheckInResponse(
                checkIn.getId(),
                checkIn.getTrip().getId(),
                checkIn.getUser() == null ? null : checkIn.getUser().getId(),
                checkIn.getSource(),
                checkIn.getLatitude(),
                checkIn.getLongitude(),
                checkIn.getBatteryPercent(),
                checkIn.getMessage(),
                checkIn.getCreatedAt());
    }
}
