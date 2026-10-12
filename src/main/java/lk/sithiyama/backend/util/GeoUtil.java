package lk.sithiyama.backend.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lk.sithiyama.backend.domain.GeoPoint;

/**
 * Geometry helpers shared across the service layer. Deliberately dependency-free
 * (no JTS): paths travel as WKT text so the same code runs against PostGIS and
 * against the in-memory H2 development profile.
 */
public final class GeoUtil {

    /** Mean earth radius in metres (IUGG). */
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;
    private static final double METERS_PER_DEGREE_LATITUDE = 111_320.0;

    private GeoUtil() {
    }

    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Total walked distance along a polyline. */
    public static double pathLengthMeters(List<GeoPoint> points) {
        if (points == null || points.size() < 2) {
            return 0d;
        }
        double total = 0d;
        for (int i = 1; i < points.size(); i++) {
            GeoPoint a = points.get(i - 1);
            GeoPoint b = points.get(i);
            total += haversineMeters(a.latitude(), a.longitude(), b.latitude(), b.longitude());
        }
        return total;
    }

    /** Serialises a polyline as a PostGIS-parseable WKT LINESTRING (lon lat order). */
    public static String toLineStringWkt(List<GeoPoint> points) {
        if (points == null || points.size() < 2) {
            return null;
        }
        StringBuilder sb = new StringBuilder("LINESTRING(");
        for (int i = 0; i < points.size(); i++) {
            GeoPoint p = points.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            // Locale.US keeps the decimal separator a dot regardless of host locale.
            sb.append(String.format(Locale.US, "%.6f %.6f", p.longitude(), p.latitude()));
        }
        return sb.append(')').toString();
    }

    /** Parses a WKT LINESTRING produced by {@link #toLineStringWkt(List)}. */
    public static List<GeoPoint> parseLineStringWkt(String wkt) {
        List<GeoPoint> points = new ArrayList<>();
        if (wkt == null || wkt.isBlank()) {
            return points;
        }
        String body = wkt.trim();
        int open = body.indexOf('(');
        int close = body.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return points;
        }
        body = body.substring(open + 1, close);
        for (String pair : body.split(",")) {
            String[] xy = pair.trim().split("\\s+");
            if (xy.length < 2) {
                continue;
            }
            try {
                double lon = Double.parseDouble(xy[0]);
                double lat = Double.parseDouble(xy[1]);
                points.add(new GeoPoint(lat, lon));
            } catch (NumberFormatException ignored) {
                // Skip malformed coordinates rather than failing the whole read.
            }
        }
        return points;
    }

    public static BoundingBox boundingBox(List<GeoPoint> points) {
        double minLat = Double.POSITIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        for (GeoPoint p : points) {
            minLat = Math.min(minLat, p.latitude());
            minLon = Math.min(minLon, p.longitude());
            maxLat = Math.max(maxLat, p.latitude());
            maxLon = Math.max(maxLon, p.longitude());
        }
        return new BoundingBox(minLat, minLon, maxLat, maxLon);
    }

    /** Approximate bounding box around a point, used by the non-PostGIS fallback. */
    public static BoundingBox boundingBoxAround(double latitude, double longitude, double radiusMeters) {
        double deltaLat = radiusMeters / METERS_PER_DEGREE_LATITUDE;
        double cosLat = Math.max(0.01, Math.cos(Math.toRadians(latitude)));
        double deltaLon = radiusMeters / (METERS_PER_DEGREE_LATITUDE * cosLat);
        return new BoundingBox(latitude - deltaLat, longitude - deltaLon, latitude + deltaLat, longitude + deltaLon);
    }

    public record BoundingBox(double minLatitude, double minLongitude, double maxLatitude, double maxLongitude) {
    }
}
