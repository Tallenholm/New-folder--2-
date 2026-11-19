package com.mapledger.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceEngineTest {
    private fun miles(fixes: List<RawFix>, cfg: EngineConfig = EngineConfig()) =
        GeoMath.metersToMiles(DistanceEngine.replay(fixes, cfg).first.snapshot().verifiedMeters)

    @Test fun haversineKnownDistance() {
        // Chicago Union Station → Manteno village centre ≈ 71.6 km great-circle (hand-computed).
        val d = GeoMath.haversineMeters(41.8786, -87.6403, 41.2506, -87.8314)
        assertEquals(71_400.0, d, 1_500.0)
        // 1° latitude ≈ 111.2 km
        assertEquals(111_195.0, GeoMath.haversineMeters(41.0, -87.0, 42.0, -87.0), 50.0)
    }

    @Test fun straightTenMilesIsAccurate() {
        val fixes = DriveSim.drive(listOf(Triple(0.0, GeoMath.milesToMeters(10.0), 25.0)))
        assertEquals(10.0, miles(fixes), 0.01)
    }

    @Test fun homeToHomeLoopWithNoiseWithin1Percent() {
        // Home → 12 mi north → 3 mi east → back home: 30 mi round trip at ~55 mph with 3 m GPS noise.
        val leg = GeoMath.milesToMeters(12.0); val side = GeoMath.milesToMeters(3.0)
        val fixes = DriveSim.drive(listOf(Triple(0.0, leg, 24.0), Triple(90.0, side, 15.0), Triple(180.0, leg, 24.0), Triple(270.0, side, 15.0)), noiseM = 3.0)
        val m = miles(fixes)
        assertEquals(30.0, m, 0.3)
        // Ends back near home (a lat/lon "square" doesn't close exactly on a sphere: ~13 m here).
        assertTrue(GeoMath.haversineMeters(fixes.first().lat, fixes.first().lon, fixes.last().lat, fixes.last().lon) < 30)
    }

    @Test fun stationaryJitterAddsAlmostNothing() {
        val drive = DriveSim.drive(listOf(Triple(0.0, 1609.344, 20.0)))
        val last = drive.last()
        val parked = DriveSim.stationary(last.lat, last.lon, last.time, 20 * 60_000L, jitterM = 6.0)
        val total = miles(drive + parked)
        assertEquals(1.0, total, 0.02)
        val snap = DistanceEngine.replay(drive + parked).first.snapshot()
        assertTrue("stationary time recorded", snap.stationaryMs > 15 * 60_000L)
    }

    @Test fun duplicatesAreIgnored() {
        val fixes = DriveSim.drive(listOf(Triple(45.0, 5_000.0, 20.0)))
        val duplicated = fixes.flatMap { listOf(it, it, it) }
        val (eng, flags) = DistanceEngine.replay(duplicated)
        assertEquals(miles(fixes), GeoMath.metersToMiles(eng.snapshot().verifiedMeters), 1e-9)
        assertEquals(fixes.size * 2, flags.count { it == FixFlag.DUPLICATE })
    }

    @Test fun impossibleSpeedSpikeRejected() {
        val fixes = DriveSim.drive(listOf(Triple(0.0, 3_000.0, 20.0))).toMutableList()
        val mid = fixes[fixes.size / 2]
        val (sl, so) = GeoMath.destination(mid.lat, mid.lon, 90.0, 5_000.0) // 5 km jump in 1 s
        fixes.add(fixes.size / 2 + 1, RawFix(mid.time + 1000, sl, so, 5f, 20f))
        val (eng, flags) = DistanceEngine.replay(fixes)
        assertTrue(flags.contains(FixFlag.IMPOSSIBLE_SPEED))
        assertEquals(3_000.0, eng.snapshot().verifiedMeters, 30.0)
    }

    @Test fun lowAccuracyFixesRejected() {
        val good = DriveSim.drive(listOf(Triple(0.0, 2_000.0, 20.0)))
        val bad = good.mapIndexed { i, f -> if (i % 5 == 2) f.copy(accuracyM = 120f, lat = f.lat + 0.002) else f }
        val (eng, flags) = DistanceEngine.replay(bad)
        assertTrue(flags.count { it == FixFlag.LOW_ACCURACY } > 0)
        assertEquals(2_000.0, eng.snapshot().verifiedMeters, 25.0)
    }

    @Test fun largeGpsGapIsNotCountedButRecorded() {
        // 2 mi driven, then 10 minutes with no GPS (tunnel / phone in pocket) covering 6 mi, then 2 mi more.
        val a = DriveSim.drive(listOf(Triple(0.0, GeoMath.milesToMeters(2.0), 25.0)))
        val gapStart = a.last()
        val (gl, go) = GeoMath.destination(gapStart.lat, gapStart.lon, 0.0, GeoMath.milesToMeters(6.0))
        val b = DriveSim.drive(listOf(Triple(0.0, GeoMath.milesToMeters(2.0), 25.0)), startTime = gapStart.time + 10 * 60_000L, lat0 = gl, lon0 = go)
        val snap = DistanceEngine.replay(a + b).first.snapshot()
        assertEquals(4.0, GeoMath.metersToMiles(snap.verifiedMeters), 0.02)
        assertEquals(1, snap.gaps.size)
        assertEquals(6.0, GeoMath.metersToMiles(snap.gapStraightLineMeters), 0.02)
    }

    @Test fun shortSampleDropoutStillConnected() {
        // 30 s without fixes at highway speed is normal; still joined (well under gap thresholds).
        val a = DriveSim.drive(listOf(Triple(0.0, 1_000.0, 25.0)))
        val e = a.last()
        val (l, o) = GeoMath.destination(e.lat, e.lon, 0.0, 200.0)
        val b = DriveSim.drive(listOf(Triple(0.0, 1_000.0, 25.0)), startTime = e.time + 30_000, lat0 = l, lon0 = o)
        val snap = DistanceEngine.replay(a + b).first.snapshot()
        assertEquals(2_200.0, snap.verifiedMeters, 20.0)
        assertEquals(0, snap.gaps.size)
    }

    @Test fun pauseSegmentsAreNeverJoined() {
        val a = DriveSim.drive(listOf(Triple(0.0, 3_000.0, 20.0)), segment = 0)
        val e = a.last()
        val (l, o) = GeoMath.destination(e.lat, e.lon, 90.0, 4_000.0) // personal errand while paused
        val b = DriveSim.drive(listOf(Triple(0.0, 3_000.0, 20.0)), startTime = e.time + 3_600_000, lat0 = l, lon0 = o, segment = 1)
        val snap = DistanceEngine.replay(a + b).first.snapshot()
        assertEquals(6_000.0, snap.verifiedMeters, 30.0)
        assertEquals(0.0, snap.gapStraightLineMeters, 0.0)
    }

    @Test fun replayAfterCrashMatchesLiveTotals() {
        // Simulates process death mid-trip: the service rebuilds its engine from stored fixes.
        val fixes = DriveSim.drive(listOf(Triple(10.0, 8_000.0, 22.0), Triple(100.0, 4_000.0, 15.0)), noiseM = 2.0)
        val live = DistanceEngine()
        val cut = fixes.size / 2
        fixes.take(cut).forEach { live.offer(it) }
        val beforeCrash = live.snapshot().verifiedMeters
        // "Crash": rebuild from what was persisted, then keep going.
        val (rebuilt, _) = DistanceEngine.replay(fixes.take(cut))
        assertEquals(beforeCrash, rebuilt.snapshot().verifiedMeters, 1e-9)
        fixes.drop(cut).forEach { live.offer(it); rebuilt.offer(it) }
        assertEquals(live.snapshot().verifiedMeters, rebuilt.snapshot().verifiedMeters, 1e-9)
    }

    @Test fun interruptionDowntimeBecomesUnverifiedGap() {
        // Process killed for 25 min; recovery resumes in the same segment -> gap recorded, not counted.
        val a = DriveSim.drive(listOf(Triple(0.0, 5_000.0, 20.0)))
        val e = a.last()
        val (l, o) = GeoMath.destination(e.lat, e.lon, 0.0, 15_000.0)
        val b = DriveSim.drive(listOf(Triple(0.0, 5_000.0, 20.0)), startTime = e.time + 25 * 60_000L, lat0 = l, lon0 = o)
        val snap = DistanceEngine.replay(a + b).first.snapshot()
        assertEquals(10_000.0, snap.verifiedMeters, 40.0)
        assertEquals(15_000.0, snap.gapStraightLineMeters, 20.0)
    }
}
