package com.kiefer.arzttermin.location;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Minimal synthetic postal-code resolver for the Berlin pilot.
 *
 * <p>It deliberately contains no external geocoding data. Known postal codes
 * use Haversine distance. Unknown equal postal codes get a conservative local
 * fallback of zero kilometres; two different unresolved postal codes never
 * match a finite search radius.</p>
 */
@Service
public class PostalCodeGeoService {

    private static final Pattern GERMAN_POSTAL_CODE = Pattern.compile("\\d{5}");
    private static final double EARTH_RADIUS_KM = 6_371.0088;
    private static final double UNRESOLVED_DISTANCE_KM = Double.POSITIVE_INFINITY;

    private static final Map<String, GeoPoint> SYNTHETIC_COORDINATES = Map.of(
            "10115", new GeoPoint(52.5324, 13.3848),
            "10117", new GeoPoint(52.5163, 13.3889),
            "10119", new GeoPoint(52.5295, 13.4077),
            "10999", new GeoPoint(52.4997, 13.4311));

    public Optional<GeoPoint> resolve(String postalCode) {
        return Optional.ofNullable(SYNTHETIC_COORDINATES.get(normalize(postalCode)));
    }

    /**
     * Estimates distance and records whether the result came from two known
     * synthetic coordinates.
     */
    public Distance estimateDistance(String fromPostalCode, String toPostalCode) {
        String from = normalize(fromPostalCode);
        String to = normalize(toPostalCode);
        Optional<GeoPoint> fromPoint = resolve(from);
        Optional<GeoPoint> toPoint = resolve(to);
        if (fromPoint.isPresent() && toPoint.isPresent()) {
            return new Distance(haversineKilometers(fromPoint.get(), toPoint.get()), true);
        }
        if (from.equals(to)) {
            return new Distance(0.0, false);
        }
        return new Distance(UNRESOLVED_DISTANCE_KM, false);
    }

    public double distanceInKilometers(String fromPostalCode, String toPostalCode) {
        return estimateDistance(fromPostalCode, toPostalCode).kilometers();
    }

    public boolean isWithinRadius(String fromPostalCode, String toPostalCode, double radiusKm) {
        if (Double.isNaN(radiusKm) || radiusKm < 0.0) {
            throw new IllegalArgumentException("radiusKm must be non-negative");
        }
        return distanceInKilometers(fromPostalCode, toPostalCode) <= radiusKm;
    }

    public double haversineKilometers(GeoPoint first, GeoPoint second) {
        double latitudeDelta = Math.toRadians(second.latitude() - first.latitude());
        double longitudeDelta = Math.toRadians(second.longitude() - first.longitude());
        double firstLatitude = Math.toRadians(first.latitude());
        double secondLatitude = Math.toRadians(second.latitude());

        double a = Math.pow(Math.sin(latitudeDelta / 2.0), 2)
                + Math.cos(firstLatitude) * Math.cos(secondLatitude)
                * Math.pow(Math.sin(longitudeDelta / 2.0), 2);
        double centralAngle = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * centralAngle;
    }

    private static String normalize(String postalCode) {
        if (postalCode == null) {
            throw new IllegalArgumentException("postalCode must not be null");
        }
        String normalized = postalCode.strip();
        if (!GERMAN_POSTAL_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid German postal code: " + postalCode);
        }
        return normalized;
    }

    public record GeoPoint(double latitude, double longitude) {
        public GeoPoint {
            if (latitude < -90.0 || latitude > 90.0) {
                throw new IllegalArgumentException("latitude is outside [-90, 90]");
            }
            if (longitude < -180.0 || longitude > 180.0) {
                throw new IllegalArgumentException("longitude is outside [-180, 180]");
            }
        }
    }

    public record Distance(double kilometers, boolean resolvedFromCoordinates) {
        public Distance {
            if (Double.isNaN(kilometers) || kilometers < 0.0) {
                throw new IllegalArgumentException("kilometers must be non-negative");
            }
        }
    }
}
