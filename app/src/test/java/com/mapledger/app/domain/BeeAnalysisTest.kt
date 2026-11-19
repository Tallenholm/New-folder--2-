package com.mapledger.app.domain

import org.junit.Assert.*
import org.junit.Test

class BeeAnalysisTest {
    private val fixes = DriveSim.drive(listOf(Triple(90.0, 5_000.0, 20.0))).map { LatLon(it.lat, it.lon) }

    @Test fun targetOnRouteIsPassed() {
        val mid = fixes[fixes.size / 2]
        val (l, o) = GeoMath.destination(mid.lat, mid.lon, 0.0, 30.0) // 30 m off the road
        assertNotNull(BeeAnalysis.firstPass(LatLon(l, o), 75.0, fixes))
    }

    @Test fun targetFarAwayIsNotPassed() {
        val mid = fixes[fixes.size / 2]
        val (l, o) = GeoMath.destination(mid.lat, mid.lon, 0.0, 400.0)
        assertNull(BeeAnalysis.firstPass(LatLon(l, o), 75.0, fixes))
    }

    @Test fun passBetweenSparseFixesIsDetected() {
        val a = LatLon(41.25, -87.83)
        val (bl, bo) = GeoMath.destination(a.lat, a.lon, 90.0, 600.0)
        val (tl, to) = GeoMath.destination(a.lat, a.lon, 90.0, 300.0)
        assertNotNull(BeeAnalysis.firstPass(LatLon(tl, to), 40.0, listOf(a, LatLon(bl, bo))))
    }

    @Test fun gapsAreNotBridgedForPasses() {
        val a = LatLon(41.25, -87.83)
        val (bl, bo) = GeoMath.destination(a.lat, a.lon, 90.0, 10_000.0)
        val (tl, to) = GeoMath.destination(a.lat, a.lon, 90.0, 5_000.0)
        assertNull("never claim a pass across a GPS gap", BeeAnalysis.firstPass(LatLon(tl, to), 75.0, listOf(a, LatLon(bl, bo))))
    }

    @Test fun routeCoverageHalfDriven() {
        val route = BeeAnalysis.densify(listOf(fixes.first(), fixes.last()))
        val half = fixes.take(fixes.size / 2)
        val f = BeeAnalysis.coveredRouteIndices(route, half).size.toDouble() / route.size
        assertEquals(0.5, f, 0.05)
    }

    @Test fun overlapDetection() {
        val a = listOf(fixes.first(), fixes.last())
        assertEquals(1.0, BeeAnalysis.overlapFraction(a, BeeAnalysis.densify(a)), 0.01)
        val far = a.map { LatLon(it.lat + 0.05, it.lon) }
        assertEquals(0.0, BeeAnalysis.overlapFraction(a, BeeAnalysis.densify(far)), 0.0)
    }
}
