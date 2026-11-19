package com.mapledger.app.domain

import kotlin.random.Random

/** Synthesises realistic GPS fix streams for tests. */
object DriveSim {
    const val START_LAT = 41.2506
    const val START_LON = -87.8314

    /** Drive [legs] of (bearing°, meters, speed m/s) from a start point, sampling every [stepMs]. */
    fun drive(
        legs: List<Triple<Double, Double, Double>>, stepMs: Long = 2000, startTime: Long = 1_790_000_000_000L,
        accuracy: Float = 5f, noiseM: Double = 0.0, seed: Int = 1, segment: Int = 0,
        lat0: Double = START_LAT, lon0: Double = START_LON,
    ): List<RawFix> {
        val rnd = Random(seed)
        val out = mutableListOf<RawFix>()
        var lat = lat0; var lon = lon0; var t = startTime
        out += RawFix(t, lat, lon, accuracy, 0f, segment)
        for ((bearing, dist, speed) in legs) {
            val stepM = speed * stepMs / 1000.0
            var done = 0.0
            while (done + 1e-9 < dist) {
                val d = minOf(stepM, dist - done)
                val p = GeoMath.destination(lat, lon, bearing, d)
                lat = p.first; lon = p.second; done += d
                t += (d / speed * 1000).toLong().coerceAtLeast(1)
                val (nl, no) = if (noiseM > 0) GeoMath.destination(lat, lon, rnd.nextDouble(0.0, 360.0), rnd.nextDouble(0.0, noiseM)) else lat to lon
                out += RawFix(t, nl, no, accuracy, speed.toFloat(), segment)
            }
        }
        return out
    }

    fun stationary(lat: Double, lon: Double, fromTime: Long, durationMs: Long, stepMs: Long = 2000, jitterM: Double = 4.0, accuracy: Float = 8f, seed: Int = 7, segment: Int = 0): List<RawFix> {
        val rnd = Random(seed)
        return (1..(durationMs / stepMs)).map { i ->
            val (a, b) = GeoMath.destination(lat, lon, rnd.nextDouble(0.0, 360.0), rnd.nextDouble(0.0, jitterM))
            RawFix(fromTime + i * stepMs, a, b, accuracy, 0.1f, segment)
        }
    }
}
