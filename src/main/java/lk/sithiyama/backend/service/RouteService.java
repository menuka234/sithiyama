package lk.sithiyama.backend.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.sithiyama.backend.domain.GeoPoint;
import lk.sithiyama.backend.domain.Route;
import lk.sithiyama.backend.domain.RouteWaypoint;
import lk.sithiyama.backend.domain.User;
import lk.sithiyama.backend.dto.CommonDtos.PageResponse;
import lk.sithiyama.backend.dto.RouteDtos.RouteRequest;
import lk.sithiyama.backend.dto.RouteDtos.RouteResponse;
import lk.sithiyama.backend.dto.RouteDtos.RouteSearchCriteria;
import lk.sithiyama.backend.dto.RouteDtos.RouteSummary;
import lk.sithiyama.backend.dto.RouteDtos.WaypointRequest;
import lk.sithiyama.backend.dto.RouteDtos.WaypointResponse;
import lk.sithiyama.backend.exception.BadRequestException;
import lk.sithiyama.backend.exception.ForbiddenException;
import lk.sithiyama.backend.exception.ResourceNotFoundException;
import lk.sithiyama.backend.repository.RouteRepository;
import lk.sithiyama.backend.repository.UserRepository;
import lk.sithiyama.backend.security.AppUserPrincipal;
import lk.sithiyama.backend.security.SecurityUtils;
import lk.sithiyama.backend.util.GeoUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The community route library: guides publish the paths they know, hikers find
 * and follow them, offline or online.
 */
@Service
public class RouteService {

    /** Walking speed used to estimate duration when the client does not supply one. */
    private static final double ESTIMATE_WALKING_KMH = 3.0;
    private static final int MAX_SEARCH_RESULTS = 500;
    private static final int MAX_TAGS = 20;

    private final RouteRepository routeRepository;
    private final UserRepository userRepository;
    private final boolean spatialEnabled;

    public RouteService(RouteRepository routeRepository,
                        UserRepository userRepository,
                        @Value("${sithiyama.spatial.enabled:false}") boolean spatialEnabled) {
        this.routeRepository = routeRepository;
        this.userRepository = userRepository;
        this.spatialEnabled = spatialEnabled;
    }

    @Transactional
    public RouteResponse create(RouteRequest request) {
        Route route = new Route();
        apply(route, request);
        route.setCreator(currentUser());
        return toResponse(routeRepository.save(route));
    }

    @Transactional(readOnly = true)
    public RouteResponse get(UUID id) {
        Route route = requireRoute(id);
        assertReadable(route);
        return toResponse(route);
    }

    @Transactional(readOnly = true)
    public PageResponse<RouteSummary> search(RouteSearchCriteria criteria, Pageable pageable) {
        String qLike = likePattern(criteria.q());
        String region = blankToNull(criteria.region());

        boolean radiusSearch = spatialEnabled
                && criteria.nearLatitude() != null
                && criteria.nearLongitude() != null
                && criteria.radiusMeters() != null
                && !criteria.mineOnly();

        if (radiusSearch) {
            return radiusSearch(criteria, qLike, region, pageable);
        }

        List<Route.Visibility> visibilities = criteria.mineOnly()
                ? List.of(Route.Visibility.PUBLIC, Route.Visibility.UNLISTED, Route.Visibility.PRIVATE)
                : List.of(Route.Visibility.PUBLIC, Route.Visibility.UNLISTED);
        UUID ownerId = criteria.mineOnly() ? SecurityUtils.requireUserId() : null;

        Double minLat = null;
        Double maxLat = null;
        Double minLon = null;
        Double maxLon = null;
        if (criteria.nearLatitude() != null && criteria.nearLongitude() != null && criteria.radiusMeters() != null) {
            // No PostGIS available: approximate the radius with a bounding box.
            GeoUtil.BoundingBox box = GeoUtil.boundingBoxAround(
                    criteria.nearLatitude(), criteria.nearLongitude(), criteria.radiusMeters());
            minLat = box.minLatitude();
            maxLat = box.maxLatitude();
            minLon = box.minLongitude();
            maxLon = box.maxLongitude();
        }

        Page<Route> page = routeRepository.search(
                visibilities,
                ownerId,
                qLike,
                region,
                criteria.difficulty(),
                criteria.maxDistanceMeters(),
                criteria.minDistanceMeters(),
                minLat,
                maxLat,
                minLon,
                maxLon,
                pageable);

        return PageResponse.of(page.map(this::toSummary));
    }

    @Transactional
    public RouteResponse update(UUID id, RouteRequest request) {
        Route route = requireRoute(id);
        assertWritable(route);
        apply(route, request);
        return toResponse(routeRepository.save(route));
    }

    @Transactional
    public void delete(UUID id) {
        Route route = requireRoute(id);
        assertWritable(route);
        routeRepository.delete(route);
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private PageResponse<RouteSummary> radiusSearch(RouteSearchCriteria criteria, String qLike,
                                                    String region, Pageable pageable) {
        int size = pageable.getPageSize();
        int number = pageable.getPageNumber();
        int limit = Math.min(Math.max(size, (number + 1) * size), MAX_SEARCH_RESULTS);

        List<UUID> orderedIds = routeRepository.findNearbyRouteIds(
                criteria.nearLatitude(),
                criteria.nearLongitude(),
                criteria.radiusMeters(),
                qLike,
                region,
                criteria.difficulty() == null ? null : criteria.difficulty().name(),
                limit);

        long total = orderedIds.size();
        List<UUID> pageIds = orderedIds.stream()
                .skip((long) number * size)
                .limit(size)
                .toList();

        Map<UUID, Route> byId = routeRepository.findAllById(pageIds).stream()
                .collect(Collectors.toMap(Route::getId, Function.identity()));

        List<RouteSummary> content = pageIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(this::toSummary)
                .toList();

        int totalPages = size == 0 ? 0 : (int) Math.ceil(total / (double) size);
        boolean last = (long) (number + 1) * size >= total;
        return new PageResponse<>(content, number, size, total, totalPages, last);
    }

    private void apply(Route route, RouteRequest request) {
        List<GeoPoint> path = request.path();
        if (path == null || path.size() < 2) {
            throw new BadRequestException("A route path needs at least two points");
        }

        GeoUtil.BoundingBox box = GeoUtil.boundingBox(path);
        GeoPoint first = path.get(0);
        GeoPoint last = path.get(path.size() - 1);
        double computedDistance = GeoUtil.pathLengthMeters(path);
        double distance = request.distanceMeters() != null && request.distanceMeters() > 0
                ? request.distanceMeters()
                : computedDistance;

        route.setName(request.name().trim());
        route.setDescription(request.description());
        route.setRegion(blankToNull(request.region()));
        route.setDifficulty(request.difficulty() == null ? Route.Difficulty.MODERATE : request.difficulty());
        route.setVisibility(request.visibility() == null ? Route.Visibility.PUBLIC : request.visibility());
        route.setDistanceMeters(distance);
        route.setAscentMeters(request.ascentMeters() == null ? 0d : request.ascentMeters());
        route.setEstimatedDurationMinutes(request.estimatedDurationMinutes() != null
                ? request.estimatedDurationMinutes()
                : estimateMinutes(distance));
        route.setStartLatitude(first.latitude());
        route.setStartLongitude(first.longitude());
        route.setEndLatitude(last.latitude());
        route.setEndLongitude(last.longitude());
        route.setMinLatitude(box.minLatitude());
        route.setMinLongitude(box.minLongitude());
        route.setMaxLatitude(box.maxLatitude());
        route.setMaxLongitude(box.maxLongitude());
        route.setPathWkt(GeoUtil.toLineStringWkt(path));
        route.setTags(normalizeTags(request.tags()));

        route.clearWaypoints();
        List<WaypointRequest> waypoints = request.waypoints() == null ? List.of() : request.waypoints();
        int sequence = 1;
        for (WaypointRequest waypoint : waypoints) {
            RouteWaypoint entity = new RouteWaypoint();
            entity.setSequenceNumber(waypoint.sequenceNumber() != null ? waypoint.sequenceNumber() : sequence);
            entity.setName(waypoint.name());
            entity.setType(waypoint.type() == null ? RouteWaypoint.WaypointType.CHECKPOINT : waypoint.type());
            entity.setLatitude(waypoint.latitude());
            entity.setLongitude(waypoint.longitude());
            entity.setElevationMeters(waypoint.elevationMeters());
            entity.setNote(waypoint.note());
            route.addWaypoint(entity);
            sequence++;
        }
    }

    private Set<String> normalizeTags(Set<String> tags) {
        Set<String> normalized = new LinkedHashSet<>();
        if (tags == null) {
            return normalized;
        }
        tags.stream()
                .filter(Objects::nonNull)
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isEmpty())
                .limit(MAX_TAGS)
                .forEach(normalized::add);
        return normalized;
    }

    private Route requireRoute(UUID id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Route", id));
    }

    private void assertReadable(Route route) {
        if (route.getVisibility() != Route.Visibility.PRIVATE) {
            return;
        }
        Optional<AppUserPrincipal> principal = SecurityUtils.currentPrincipal();
        boolean allowed = principal.isPresent()
                && (principal.get().id().equals(route.getCreator().getId())
                    || principal.get().role() == User.Role.ROLE_ADMIN);
        if (!allowed) {
            throw ResourceNotFoundException.of("Route", route.getId());
        }
    }

    private void assertWritable(Route route) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        boolean allowed = principal.id().equals(route.getCreator().getId())
                || principal.role() == User.Role.ROLE_ADMIN;
        if (!allowed) {
            throw new ForbiddenException("Only the route creator or an admin can change this route");
        }
    }

    private User currentUser() {
        return userRepository.findById(SecurityUtils.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Account no longer exists"));
    }

    private RouteResponse toResponse(Route route) {
        return new RouteResponse(
                route.getId(),
                route.getName(),
                route.getDescription(),
                route.getRegion(),
                route.getDifficulty(),
                route.getVisibility(),
                route.getDistanceMeters(),
                route.getAscentMeters(),
                route.getEstimatedDurationMinutes(),
                route.getStartLatitude(),
                route.getStartLongitude(),
                route.getEndLatitude(),
                route.getEndLongitude(),
                new LinkedHashSet<>(route.getTags()),
                GeoUtil.parseLineStringWkt(route.getPathWkt()),
                route.getWaypoints().stream().map(this::toWaypointResponse).toList(),
                route.getCreator().getId(),
                route.getCreator().getDisplayName(),
                route.getCreatedAt(),
                route.getUpdatedAt());
    }

    private RouteSummary toSummary(Route route) {
        return new RouteSummary(
                route.getId(),
                route.getName(),
                route.getDescription(),
                route.getRegion(),
                route.getDifficulty(),
                route.getVisibility(),
                route.getDistanceMeters(),
                route.getAscentMeters(),
                route.getEstimatedDurationMinutes(),
                route.getStartLatitude(),
                route.getStartLongitude(),
                route.getEndLatitude(),
                route.getEndLongitude(),
                new LinkedHashSet<>(route.getTags()),
                route.getCreator().getId(),
                route.getCreator().getDisplayName(),
                route.getCreatedAt(),
                route.getUpdatedAt());
    }

    private WaypointResponse toWaypointResponse(RouteWaypoint waypoint) {
        return new WaypointResponse(
                waypoint.getId(),
                waypoint.getSequenceNumber(),
                waypoint.getName(),
                waypoint.getType(),
                waypoint.getLatitude(),
                waypoint.getLongitude(),
                waypoint.getElevationMeters(),
                waypoint.getNote());
    }

    private static int estimateMinutes(double distanceMeters) {
        return (int) Math.round(distanceMeters / 1000.0 / ESTIMATE_WALKING_KMH * 60.0);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String likePattern(String value) {
        return value == null || value.isBlank()
                ? null
                : "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
