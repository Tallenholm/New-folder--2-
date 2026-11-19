package com.mapledger.app.data.repo

import com.mapledger.app.data.db.AppDatabase
import com.mapledger.app.data.db.BurstStatus
import com.mapledger.app.data.db.BurstTarget
import com.mapledger.app.data.db.HistoricalGoal
import com.mapledger.app.data.db.PaymentStatus
import com.mapledger.app.data.db.PlannedRoute
import com.mapledger.app.data.db.RoutePoint
import com.mapledger.app.domain.BeeAnalysis
import com.mapledger.app.domain.FixFlag
import com.mapledger.app.domain.LatLon

class BeeRepository(private val db: AppDatabase, private val settings: SettingsRepository) {
    private val bee = db.bee()
    fun observeRoutes() = bee.observeRoutes()
    fun observeCoverage() = bee.observeCoverage()
    fun observeTargets() = bee.observeTargets()
    fun observeGoals() = bee.observeGoals()
    suspend fun routePoints(id: Long) = bee.routePoints(id)

    suspend fun addRoute(name: String, file: String, pts: List<LatLon>, hash: String): Long? {
        if (pts.size < 2 || bee.countRouteHash(hash) > 0) return null
        val id = bee.insertRoute(PlannedRoute(name = name, sourceFile = file, lengthMeters = BeeAnalysis.polylineMeters(pts), pointCount = pts.size, contentHash = hash))
        // Densify so coverage is measured every ~20 m, not only at sparse route vertices.
        bee.insertRoutePoints(BeeAnalysis.densify(pts).mapIndexed { i, p -> RoutePoint(routeId = id, seq = i, lat = p.lat, lon = p.lon) })
        // Credit coverage from trips already recorded.
        db.trips().all().forEach { analyzeTripCoverage(it.id) }
        return id
    }
    suspend fun deleteRoute(id: Long) = bee.deleteRoute(id)

    suspend fun saveTarget(t: BurstTarget) = if (t.id == 0L) bee.insertTarget(t) else { bee.updateTarget(t); t.id }
    suspend fun deleteTarget(id: Long) = bee.deleteTarget(id)
    suspend fun saveGoal(g: HistoricalGoal) = bee.upsertGoal(g)

    private suspend fun usable(tripId: Long) = db.points().forTrip(tripId)
        .filter { it.flags != FixFlag.LOW_ACCURACY && it.flags != FixFlag.DUPLICATE && it.flags != FixFlag.IMPOSSIBLE_SPEED }

    /** After a trip completes: mark route sections covered and burst targets passed (unverified). */
    suspend fun analyzeTrip(tripId: Long) {
        analyzeTripCoverage(tripId)
        val pts = usable(tripId)
        if (pts.isEmpty()) return
        val fixes = pts.map { LatLon(it.lat, it.lon) }
        bee.targets().forEach { t ->
            val idx = BeeAnalysis.firstPass(LatLon(t.lat, t.lon), t.radiusM, fixes) ?: return@forEach
            val newStatus = if (t.status == BurstStatus.PLANNED) BurstStatus.PASSED_UNVERIFIED else t.status
            bee.updateTarget(t.copy(
                status = newStatus,
                firstPassedTripId = t.firstPassedTripId ?: tripId,
                firstPassedTime = t.firstPassedTime ?: pts[idx].time,
                passCount = t.passCount + 1,
            ))
        }
    }

    private suspend fun analyzeTripCoverage(tripId: Long) {
        val pts = usable(tripId)
        if (pts.isEmpty()) return
        val fixes = pts.map { LatLon(it.lat, it.lon) }
        bee.routes().forEach { r ->
            val rp = bee.routePoints(r.id)
            val covered = BeeAnalysis.coveredRouteIndices(rp.map { LatLon(it.lat, it.lon) }, fixes)
            if (covered.isNotEmpty()) covered.map { rp[it].id }.chunked(500).forEach { bee.markCovered(it, tripId) }
        }
    }

    /**
     * Reconciliation: a burst is CREDITED only when a received payment is linked to it. Passed
     * targets with no linked payment after N days become MISSING_PAYMENT (a prompt to check Bee Maps).
     */
    suspend fun reconcile(now: Long = System.currentTimeMillis()) {
        val days = settings.current().missingPaymentDays
        val pays = db.payments().all()
        bee.targets().forEach { t ->
            val linked = pays.any { it.burstTargetId == t.id && it.status == PaymentStatus.RECEIVED }
            val status = when {
                t.status == BurstStatus.NOT_ELIGIBLE -> t.status
                linked -> BurstStatus.CREDITED
                t.firstPassedTime != null && now - t.firstPassedTime > days * 86_400_000L -> BurstStatus.MISSING_PAYMENT
                t.firstPassedTime != null -> BurstStatus.PASSED_UNVERIFIED
                else -> BurstStatus.PLANNED
            }
            if (status != t.status) bee.updateTarget(t.copy(status = status))
        }
    }

    /** Pairs of planned routes whose paths overlap by more than [threshold]. */
    suspend fun overlappingRoutes(threshold: Double = 0.3): List<Triple<PlannedRoute, PlannedRoute, Double>> {
        val routes = bee.routes()
        val pts = routes.associate { r -> r.id to bee.routePoints(r.id).map { LatLon(it.lat, it.lon) } }
        val out = mutableListOf<Triple<PlannedRoute, PlannedRoute, Double>>()
        for (i in routes.indices) for (j in i + 1 until routes.size) {
            val f = BeeAnalysis.overlapFraction(pts.getValue(routes[i].id), pts.getValue(routes[j].id))
            if (f >= threshold) out += Triple(routes[i], routes[j], f)
        }
        return out
    }
}
