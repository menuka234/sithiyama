package lk.sithiyama.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.sithiyama.backend.domain.Route;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteRepository extends JpaRepository<Route, UUID> {

    /**
     * Library search. Every filter is optional (pass {@code null} to ignore it).
     * Implemented in JPQL so it is portable across PostgreSQL/PostGIS and the
     * in-memory H2 development profile.
     */
    @Query("""
            SELECT r FROM Route r
            WHERE r.visibility IN :visibilities
              AND (:ownerId IS NULL OR r.creator.id = :ownerId)
              AND (:qLike IS NULL OR LOWER(r.name) LIKE :qLike
                   OR LOWER(COALESCE(r.description, '')) LIKE :qLike)
              AND (:region IS NULL OR LOWER(COALESCE(r.region, '')) = LOWER(:region))
              AND (:difficulty IS NULL OR r.difficulty = :difficulty)
              AND (:maxDistanceMeters IS NULL OR r.distanceMeters <= :maxDistanceMeters)
              AND (:minDistanceMeters IS NULL OR r.distanceMeters >= :minDistanceMeters)
              AND (:minLat IS NULL OR r.maxLatitude >= :minLat)
              AND (:maxLat IS NULL OR r.minLatitude <= :maxLat)
              AND (:minLon IS NULL OR r.maxLongitude >= :minLon)
              AND (:maxLon IS NULL OR r.minLongitude <= :maxLon)
            """)
    @SuppressWarnings("java:S107")
    Page<Route> search(
            @Param("visibilities") Collection<Route.Visibility> visibilities,
            @Param("ownerId") UUID ownerId,
            @Param("qLike") String qLike,
            @Param("region") String region,
            @Param("difficulty") Route.Difficulty difficulty,
            @Param("maxDistanceMeters") Double maxDistanceMeters,
            @Param("minDistanceMeters") Double minDistanceMeters,
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLon") Double minLon,
            @Param("maxLon") Double maxLon,
            Pageable pageable);

    /**
     * Radius search on the PostGIS geometry column, ordered by true distance on
     * the sphere. Only used when {@code sithiyama.spatial.enabled=true} and the
     * Flyway migrations have created {@code routes.geom}.
     */
    @Query(value = """
            SELECT r.id
            FROM routes r
            WHERE r.geom IS NOT NULL
              AND r.visibility = 'PUBLIC'
              AND (CAST(:qLike AS text) IS NULL OR LOWER(r.name) LIKE CAST(:qLike AS text))
              AND (CAST(:region AS text) IS NULL OR LOWER(COALESCE(r.region, '')) = LOWER(CAST(:region AS text)))
              AND (CAST(:difficulty AS text) IS NULL OR r.difficulty = CAST(:difficulty AS text))
              AND ST_DWithin(
                    CAST(r.geom AS geography),
                    CAST(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS geography),
                    :radiusMeters)
            ORDER BY ST_Distance(
                    CAST(r.geom AS geography),
                    CAST(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS geography)) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findNearbyRouteIds(
            @Param("lat") double latitude,
            @Param("lon") double longitude,
            @Param("radiusMeters") double radiusMeters,
            @Param("qLike") String qLike,
            @Param("region") String region,
            @Param("difficulty") String difficulty,
            @Param("limit") int limit);

    Optional<Route> findByNameIgnoreCase(String name);
}
