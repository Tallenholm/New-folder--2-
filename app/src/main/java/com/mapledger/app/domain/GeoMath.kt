package com.mapledger.app.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure geodesy helpers (no Android dependencies so they are unit-testable on the JVM). */
object GeoMath {
    const val EARTH_RADIUS_M = 6_371_008.8
    const val METERS_PER_MILE = 1609.344
    const val METERS_PER_KM = 1000.0

    /** Great-circle distance in meters (haversine). */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    /** Point at [distanceM] along [bearingDeg] from start (used by tests to synthesise drives). */
    fun destination(lat: Double, lon: Double, bearingDeg: Double, distanceM: Double): Pair<Double, Double> {
        val d = distanceM / EARTH_RADIUS_M
        val b = Math.toRadians(bearingDeg)
        val p1 = Math.toRadians(lat)
        val l1 = Math.toRadians(lon)
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
        val l2 = l1 + kotlin.math.atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        return Math.toDegrees(p2) to Math.toDegrees(l2)
    }

    fun metersToMiles(m: Double) = m / METERS_PER_MILE
    fun milesToMeters(mi: Double) = mi * METERS_PER_MILE
    fun kmToMiles(km: Double) = km * METERS_PER_KM / METERS_PER_MILE
}
