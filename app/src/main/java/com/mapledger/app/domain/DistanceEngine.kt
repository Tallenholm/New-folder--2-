package com.mapledger.app.domain

/**
 * A single raw GPS fix as delivered by FusedLocationProvider (or an import).
 * [segment] increments on every Pause→Resume so paused stretches are never connected.
 */
data class RawFix(
    val time: Long,              // epoch millis
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,        // horizontal accuracy (68% radius); <=0 means unknown
    val speedMps: Float? = null, // device-reported speed if available
    val segment: Int = 0,
)

/** Why a fix was (or wasn't) used for verified distance. Stored on each point as evidence. */
object FixFlag {
    const val ACCEPTED = 0
    const val LOW_ACCURACY = 1 shl 0
    const val DUPLICATE = 1 shl 1
    const val IMPOSSIBLE_SPEED = 1 shl 2
    const val STATIONARY = 1 shl 3
    const val GAP_BEFORE = 1 shl 4      // fix follows a GPS gap; the gap itself is NOT counted as verified distance
    const val SEGMENT_START = 1 shl 5   // first fix after start/resume

    fun describe(flags: Int): String = buildList {
        if (flags == ACCEPTED) add("ok")
        if (flags and LOW_ACCURACY != 0) add("low-accuracy")
        if (flags and DUPLICATE != 0) add("duplicate")
        if (flags and IMPOSSIBLE_SPEED != 0) add("impossible-speed")
        if (flags and STATIONARY != 0) add("stationary")
        if (flags and GAP_BEFORE != 0) add("after-gap")
        if (flags and SEGMENT_START != 0) add("segment-start")
    }.joinToString("+")
}

data class EngineConfig(
    /** Fixes worse than this are kept as evidence but not used for distance. */
    val maxAccuracyM: Float = 35f,
    /** ~200 mph. Anything faster between two fixes is a GPS spike. */
    val maxSpeedMps: Double = 90.0,
    /** If no usable fix arrives for this long, the next movement is treated as a gap. */
    val gapTimeMs: Long = 45_000,
    /** ...unless the straight-line jump is short enough to be trusted anyway. */
    val gapTrustMeters: Double = 250.0,
    /** Minimum movement before distance accrues; scaled up by fix accuracy to suppress jitter. */
    val minStepMeters: Double = 5.0,
    /** Fraction of the combined accuracy radius treated as noise when stationary. */
    val jitterFactor: Double = 0.6,
    /** Speeds under this (from device) are treated as stopped. */
    val stoppedSpeedMps: Double = 0.8,
)

data class GapRecord(
    val startTime: Long,
    val endTime: Long,
    val straightLineMeters: Double,
    val fromLat: Double, val fromLon: Double,
    val toLat: Double, val toLon: Double,
    val reason: String,
)

data class EngineSnapshot(
    val verifiedMeters: Double,
    val gapStraightLineMeters: Double,
    val movingMs: Long,
    val stationaryMs: Long,
    val acceptedFixes: Int,
    val rejectedFixes: Int,
    val gaps: List<GapRecord>,
    val lastAccepted: RawFix?,
    val lastAccuracyM: Float?,
)

/**
 * Turns a stream of raw GPS fixes into a conservative "verified" road distance.
 *
 * Rules (deliberately conservative — this feeds a tax log):
 *  - Low-accuracy fixes, exact/near duplicates and impossible-speed spikes are rejected.
 *  - Small movements inside the accuracy radius while stopped are jitter, not distance.
 *  - Consecutive fixes are joined with straight lines (a polyline that undercounts curvy roads slightly).
 *  - A long time gap with a large jump is NOT joined: it is recorded as a [GapRecord] with its
 *    straight-line length, shown to the user as an unverified estimate, never added silently.
 *  - Different segments (pause→resume) are never joined.
 *
 * The engine is a deterministic fold, so replaying stored fixes after a crash rebuilds exactly
 * the same totals that were shown live.
 */
class DistanceEngine(private val cfg: EngineConfig = EngineConfig()) {
    private var verified = 0.0
    private var gapMeters = 0.0
    private var movingMs = 0L
    private var stationaryMs = 0L
    private var accepted = 0
    private var rejected = 0
    private val gaps = mutableListOf<GapRecord>()
    private var anchor: RawFix? = null   // last fix distance was measured from
    private var lastSeen: RawFix? = null // last fix of any kind (for duplicate detection)
    private var lastAccuracy: Float? = null

    /** Feed one fix; returns the [FixFlag] bitmask describing how it was treated. */
    fun offer(fix: RawFix): Int {
        lastAccuracy = fix.accuracyM
        val prevSeen = lastSeen
        lastSeen = fix

        if (prevSeen != null && isDuplicate(prevSeen, fix)) { rejected++; return FixFlag.DUPLICATE }
        if (fix.accuracyM > cfg.maxAccuracyM) { rejected++; return FixFlag.LOW_ACCURACY }

        val a = anchor
        if (a == null || a.segment != fix.segment) {
            anchor = fix
            accepted++
            return FixFlag.SEGMENT_START
        }
        val dt = fix.time - a.time
        if (dt <= 0) { rejected++; return FixFlag.DUPLICATE }

        val d = GeoMath.haversineMeters(a.lat, a.lon, fix.lat, fix.lon)
        val impliedSpeed = d / (dt / 1000.0)

        if (impliedSpeed > cfg.maxSpeedMps) { rejected++; return FixFlag.IMPOSSIBLE_SPEED }

        if (dt > cfg.gapTimeMs && d > cfg.gapTrustMeters) {
            gaps += GapRecord(a.time, fix.time, d, a.lat, a.lon, fix.lat, fix.lon,
                "No usable GPS for ${dt / 1000}s")
            gapMeters += d
            anchor = fix
            accepted++
            return FixFlag.GAP_BEFORE
        }

        val accA = if (a.accuracyM > 0) a.accuracyM.toDouble() else cfg.maxAccuracyM.toDouble()
        val accB = if (fix.accuracyM > 0) fix.accuracyM.toDouble() else cfg.maxAccuracyM.toDouble()
        val noise = maxOf(cfg.minStepMeters, (accA + accB) / 2.0 * cfg.jitterFactor)
        val reportedStopped = fix.speedMps != null && fix.speedMps < cfg.stoppedSpeedMps
        if (d < noise || (reportedStopped && d < noise * 2)) {
            // Stay anchored so slow creeping is still eventually counted once it exceeds the noise.
            stationaryMs += dt
            return FixFlag.STATIONARY
        }

        verified += d
        movingMs += dt
        anchor = fix
        accepted++
        return FixFlag.ACCEPTED
    }

    private fun isDuplicate(prev: RawFix, cur: RawFix): Boolean {
        if (cur.time == prev.time) return true
        val sameSpot = GeoMath.haversineMeters(prev.lat, prev.lon, cur.lat, cur.lon) < 0.05
        return sameSpot && cur.accuracyM == prev.accuracyM && cur.time - prev.time < 1_500
    }

    fun snapshot() = EngineSnapshot(
        verified, gapMeters, movingMs, stationaryMs, accepted, rejected, gaps.toList(), anchor, lastAccuracy,
    )

    companion object {
        fun replay(fixes: Iterable<RawFix>, cfg: EngineConfig = EngineConfig()): Pair<DistanceEngine, List<Int>> {
            val e = DistanceEngine(cfg)
            val flags = fixes.map { e.offer(it) }
            return e to flags
        }
    }
}
