package com.mapledger.app.domain

/** Lightweight lat/lon holder used by analysis code. */
data class LatLon(val lat: Double, val lon: Double)

object BeeAnalysis {
    /**
     * Returns the index of the first fix that came within [radiusM] of the target, or null.
     * Only usable (non-rejected) fixes should be passed in. Being near a target is evidence of a
     * pass, not proof of a credited burst.
     */
    fun firstPass(target: LatLon, radiusM: Double, fixes: List<LatLon>): Int? {
        // Also test the segment between consecutive fixes so fast passes between 2 s samples count.
        for (i in fixes.indices) {
            if (GeoMath.haversineMeters(target.lat, target.lon, fixes[i].lat, fixes[i].lon) <= radiusM) return i
            if (i > 0 && distanceToSegment(target, fixes[i - 1], fixes[i]) <= radiusM) return i
        }
        return null
    }

    /** Distance from p to segment ab in meters (equirectangular projection; fine for < 1 km segments). */
    fun distanceToSegment(p: LatLon, a: LatLon, b: LatLon): Double {
        val segLen = GeoMath.haversineMeters(a.lat, a.lon, b.lat, b.lon)
        if (segLen > 1500) return Double.MAX_VALUE // never bridge GPS gaps
        val k = Math.cos(Math.toRadians(p.lat)) * 111_320.0
        val ax = (a.lon - p.lon) * k; val ay = (a.lat - p.lat) * 110_574.0
        val bx = (b.lon - p.lon) * k; val by = (b.lat - p.lat) * 110_574.0
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx; val cy = ay + t * dy
        return Math.sqrt(cx * cx + cy * cy)
    }

    /** Indices of planned-route points that have at least one usable fix within [toleranceM]. */
    fun coveredRouteIndices(route: List<LatLon>, fixes: List<LatLon>, toleranceM: Double = 30.0): Set<Int> {
        if (route.isEmpty() || fixes.isEmpty()) return emptySet()
        // Grid bucket the fixes (~0.001° cells ≈ 111 m) for speed on long trips.
        val cell = 0.001
        val grid = HashMap<Long, MutableList<LatLon>>()
        fun key(lat: Double, lon: Double) = (Math.floor(lat / cell).toLong() shl 32) xor (Math.floor(lon / cell).toLong() and 0xffffffffL)
        fixes.forEach { grid.getOrPut(key(it.lat, it.lon)) { mutableListOf() }.add(it) }
        val out = HashSet<Int>()
        route.forEachIndexed { i, p ->
            val bLat = Math.floor(p.lat / cell).toLong(); val bLon = Math.floor(p.lon / cell).toLong()
            loop@ for (dl in -1..1) for (dn in -1..1) {
                val k = ((bLat + dl) shl 32) xor ((bLon + dn) and 0xffffffffL)
                val list = grid[k] ?: continue
                if (list.any { GeoMath.haversineMeters(p.lat, p.lon, it.lat, it.lon) <= toleranceM }) { out += i; break@loop }
            }
        }
        return out
    }

    /** Fraction (0..1) of route B's points that lie within [toleranceM] of route A — overlap detection. */
    fun overlapFraction(a: List<LatLon>, b: List<LatLon>, toleranceM: Double = 30.0): Double =
        if (b.isEmpty()) 0.0 else coveredRouteIndices(b, densify(a), toleranceM).size.toDouble() / b.size

    /** Inserts points so no two consecutive points are more than [stepM] apart (for planned routes). */
    fun densify(points: List<LatLon>, stepM: Double = 20.0): List<LatLon> {
        if (points.size < 2) return points
        val out = mutableListOf(points.first())
        for (i in 1 until points.size) {
            val a = points[i - 1]; val b = points[i]
            val d = GeoMath.haversineMeters(a.lat, a.lon, b.lat, b.lon)
            val n = (d / stepM).toInt()
            for (k in 1..n) { val t = k.toDouble() / (n + 1); out += LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t) }
            out += b
        }
        return out
    }

    fun polylineMeters(points: List<LatLon>): Double {
        var d = 0.0
        for (i in 1 until points.size) d += GeoMath.haversineMeters(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
        return d
    }
}
