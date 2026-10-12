package lk.sithiyama.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lk.sithiyama.backend.domain.GeoPoint;
import lk.sithiyama.backend.domain.TrackPoint;
import lk.sithiyama.backend.domain.TrackRecording;
import lk.sithiyama.backend.domain.User;
import lk.sithiyama.backend.dto.CommonDtos.PageResponse;
import lk.sithiyama.backend.dto.TrackDtos.TrackPointRequest;
import lk.sithiyama.backend.dto.TrackDtos.TrackPointResponse;
import lk.sithiyama.backend.dto.TrackDtos.TrackResponse;
import lk.sithiyama.backend.dto.TrackDtos.TrackSummary;
import lk.sithiyama.backend.dto.TrackDtos.TrackUploadRequest;
import lk.sithiyama.backend.exception.ResourceNotFoundException;
import lk.sithiyama.backend.repository.RouteRepository;
import lk.sithiyama.backend.repository.TrackRecordingRepository;
import lk.sithiyama.backend.repository.UserRepository;
import lk.sithiyama.backend.security.SecurityUtils;
import lk.sithiyama.backend.util.GeoUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sync hub for recorded tracks: the offline recording made on the phone, or the
 * breadcrumb log drained from a Companion Node over Bluetooth, lands here.
 */
@Service
public class TrackService {

    private final TrackRecordingRepository trackRepository;
    private final RouteRepository routeRepository;
    private final UserRepository userRepository;

    public TrackService(TrackRecordingRepository trackRepository,
                        RouteRepository routeRepository,
                        UserRepository userRepository) {
        this.trackRepository = trackRepository;
        this.routeRepository = routeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TrackResponse upload(TrackUploadRequest request) {
        User user = currentUser();
        List<TrackPointRequest> points = request.points();
        List<GeoPoint> path = points.stream()
                .map(point -> new GeoPoint(point.latitude(), point.longitude()))
                .toList();

        Instant startedAt = request.startedAt() != null
                ? request.startedAt()
                : points.stream()
                        .map(TrackPointRequest::recordedAt)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElseGet(Instant::now);

        TrackRecording recording = new TrackRecording();
        recording.setUser(user);
        if (request.routeId() != null) {
            recording.setRoute(routeRepository.findById(request.routeId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Route", request.routeId())));
        }
        recording.setName(request.name().trim());
        recording.setStatus(request.status() == null
                ? TrackRecording.TrackStatus.COMPLETED
                : request.status());
        recording.setSource(request.source() == null
                ? TrackRecording.RecordingSource.APP
                : request.source());
        recording.setStartedAt(startedAt);
        recording.setEndedAt(request.endedAt());
        recording.setDistanceMeters(GeoUtil.pathLengthMeters(path));
        recording.setPointCount(path.size());

        GeoUtil.BoundingBox box = GeoUtil.boundingBox(path);
        recording.setMinLatitude(box.minLatitude());
        recording.setMinLongitude(box.minLongitude());
        recording.setMaxLatitude(box.maxLatitude());
        recording.setMaxLongitude(box.maxLongitude());
        recording.setPathWkt(GeoUtil.toLineStringWkt(path));

        int sequence = 1;
        for (TrackPointRequest point : points) {
            TrackPoint entity = new TrackPoint();
            entity.setSequenceNumber(point.sequenceNumber() != null ? point.sequenceNumber() : sequence);
            entity.setLatitude(point.latitude());
            entity.setLongitude(point.longitude());
            entity.setElevationMeters(point.elevationMeters());
            entity.setAccuracyMeters(point.accuracyMeters());
            entity.setSpeedMetersPerSecond(point.speedMetersPerSecond());
            entity.setBatteryPercent(point.batteryPercent());
            entity.setRecordedAt(point.recordedAt() != null ? point.recordedAt() : startedAt);
            recording.addPoint(entity);
            sequence++;
        }

        return toResponse(trackRepository.save(recording));
    }

    @Transactional(readOnly = true)
    public PageResponse<TrackSummary> listMine(Pageable pageable) {
        Page<TrackRecording> page = trackRepository.findByUserId(SecurityUtils.requireUserId(), pageable);
        return PageResponse.of(page.map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public TrackResponse get(UUID id) {
        TrackRecording recording = trackRepository.findByIdAndUserId(id, SecurityUtils.requireUserId())
                .orElseThrow(() -> ResourceNotFoundException.of("Track", id));
        return toResponse(recording);
    }

    @Transactional
    public void delete(UUID id) {
        TrackRecording recording = trackRepository.findByIdAndUserId(id, SecurityUtils.requireUserId())
                .orElseThrow(() -> ResourceNotFoundException.of("Track", id));
        trackRepository.delete(recording);
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private User currentUser() {
        return userRepository.findById(SecurityUtils.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Account no longer exists"));
    }

    private TrackResponse toResponse(TrackRecording recording) {
        return new TrackResponse(
                recording.getId(),
                recording.getName(),
                recording.getRoute() == null ? null : recording.getRoute().getId(),
                recording.getRoute() == null ? null : recording.getRoute().getName(),
                recording.getUser().getId(),
                recording.getStatus(),
                recording.getSource(),
                recording.getStartedAt(),
                recording.getEndedAt(),
                recording.getDistanceMeters(),
                recording.getPointCount(),
                GeoUtil.parseLineStringWkt(recording.getPathWkt()),
                recording.getPoints().stream().map(this::toPointResponse).toList(),
                recording.getCreatedAt());
    }

    private TrackSummary toSummary(TrackRecording recording) {
        return new TrackSummary(
                recording.getId(),
                recording.getName(),
                recording.getRoute() == null ? null : recording.getRoute().getId(),
                recording.getRoute() == null ? null : recording.getRoute().getName(),
                recording.getUser().getId(),
                recording.getStatus(),
                recording.getSource(),
                recording.getStartedAt(),
                recording.getEndedAt(),
                recording.getDistanceMeters(),
                recording.getPointCount(),
                recording.getCreatedAt());
    }

    private TrackPointResponse toPointResponse(TrackPoint point) {
        return new TrackPointResponse(
                point.getId(),
                point.getSequenceNumber(),
                point.getLatitude(),
                point.getLongitude(),
                point.getElevationMeters(),
                point.getAccuracyMeters(),
                point.getSpeedMetersPerSecond(),
                point.getBatteryPercent(),
                point.getRecordedAt());
    }
}
