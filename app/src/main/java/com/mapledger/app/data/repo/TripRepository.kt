package com.mapledger.app.data.repo

import androidx.room.withTransaction
import com.mapledger.app.data.db.AppDatabase
import com.mapledger.app.data.db.AuditEntry
import com.mapledger.app.data.db.RecordSource
import com.mapledger.app.data.db.TrackPoint
import com.mapledger.app.data.db.Trip
import com.mapledger.app.data.db.TripClass
import com.mapledger.app.data.db.TripStatus
import com.mapledger.app.data.db.UploadStatus
import com.mapledger.app.domain.DistanceEngine
import com.mapledger.app.domain.EngineConfig
import com.mapledger.app.domain.EngineSnapshot
import com.mapledger.app.domain.FixFlag
import com.mapledger.app.domain.GeoMath
import com.mapledger.app.domain.Ledger
import com.mapledger.app.domain.RawFix
import kotlinx.coroutines.flow.Flow

class TripRepository(private val db: AppDatabase, private val settings: SettingsRepository) {
    private val trips = db.trips()
    private val points = db.points()
    private val audit = db.audit()

    fun observeTrips(): Flow<List<Trip>> = trips.observeAll()
    fun observeTrip(id: Long) = trips.observe(id)
    fun observePoints(id: Long) = points.observeForTrip(id)
    fun observeActive() = trips.observeActive()
    fun observeAudit(id: Long) = audit.observeFor("trip", id)
    suspend fun active() = trips.active()
    suspend fun get(id: Long) = trips.get(id)
    suspend fun points(id: Long) = points.forTrip(id)

    suspend fun engineConfig(): EngineConfig = EngineConfig(maxAccuracyM = settings.current().maxAccuracyM.toFloat())

    suspend fun startTrip(vehicleId: Long, startOdometer: Double?, odoPhoto: String?): Trip {
        val now = System.currentTimeMillis()
        val t = Trip(vehicleId = vehicleId, status = TripStatus.RECORDING, source = RecordSource.LIVE_GPS,
            startTime = now, startOdometer = startOdometer, startOdometerPhoto = odoPhoto)
        val id = trips.insert(t)
        return t.copy(id = id)
    }

    /** Persist a fix immediately (crash safety) and update running totals on the trip row. */
    suspend fun appendFix(tripId: Long, fix: RawFix, flag: Int, alt: Double?, bearing: Float?, snap: EngineSnapshot) {
        db.withTransaction {
            points.insert(TrackPoint(tripId = tripId, segment = fix.segment, time = fix.time, lat = fix.lat, lon = fix.lon,
                accuracyM = fix.accuracyM, altitudeM = alt, speedMps = fix.speedMps, bearing = bearing, flags = flag))
            val t = trips.get(tripId) ?: return@withTransaction
            val first = t.startLat == null && flag != FixFlag.LOW_ACCURACY && flag != FixFlag.DUPLICATE && flag != FixFlag.IMPOSSIBLE_SPEED
            trips.update(t.copy(
                startLat = if (first) fix.lat else t.startLat, startLon = if (first) fix.lon else t.startLon,
                gpsMeters = snap.verifiedMeters, gapMeters = snap.gapStraightLineMeters, gapCount = snap.gaps.size,
                movingMs = snap.movingMs, updatedAt = System.currentTimeMillis(),
            ))
        }
    }

    suspend fun rebuildEngine(tripId: Long): Pair<DistanceEngine, Int> {
        val pts = points.forTrip(tripId)
        val (engine, _) = DistanceEngine.replay(pts.map { RawFix(it.time, it.lat, it.lon, it.accuracyM, it.speedMps, it.segment) }, engineConfig())
        return engine to (pts.maxOfOrNull { it.segment } ?: 0)
    }

    suspend fun setStatus(tripId: Long, status: TripStatus, note: String? = null) {
        val t = trips.get(tripId) ?: return
        val now = System.currentTimeMillis()
        val updated = when (status) {
            TripStatus.PAUSED -> t.copy(status = status, pausedAt = now)
            TripStatus.RECORDING -> t.copy(status = status, pausedAt = null,
                pausedMs = t.pausedMs + (t.pausedAt?.let { now - it } ?: 0), segmentCount = t.segmentCount + if (t.status == TripStatus.PAUSED) 1 else 0)
            else -> t.copy(status = status)
        }
        trips.update(updated.copy(updatedAt = now))
        if (note != null) audit.insert(AuditEntry(entityType = "trip", entityId = tripId, field = "status", oldValue = t.status.name, newValue = status.name, reason = note))
    }

    /**
     * Finish a trip: close any pause, detect home start/end, compute confidence, flag home travel for
     * tax review. Classification is left UNCLASSIFIED — the user must confirm it.
     */
    suspend fun finalizeTrip(tripId: Long, endOdometer: Double?, endPhoto: String?, labeler: suspend (Double, Double) -> String): Trip? {
        val t = trips.get(tripId) ?: return null
        val s = settings.current()
        val now = System.currentTimeMillis()
        val pts = points.forTrip(tripId).filter { it.flags != FixFlag.LOW_ACCURACY && it.flags != FixFlag.DUPLICATE && it.flags != FixFlag.IMPOSSIBLE_SPEED }
        val first = pts.firstOrNull(); val last = pts.lastOrNull()
        fun atHome(lat: Double?, lon: Double?) = lat != null && lon != null && s.homeLat != null && s.homeLon != null &&
            GeoMath.haversineMeters(lat, lon, s.homeLat, s.homeLon) <= s.homeRadiusM
        val sh = atHome(first?.lat, first?.lon); val eh = atHome(last?.lat, last?.lon)
        val endTime = if (t.status == TripStatus.PAUSED) (t.pausedAt ?: now) else (last?.time?.coerceAtLeast(t.startTime) ?: now)
        val pausedMs = t.pausedMs // a pause still open at stop is excluded by ending the trip at pausedAt
        val draft = t.copy(
            status = TripStatus.COMPLETED, endTime = maxOf(endTime, t.startTime),
            startLat = first?.lat ?: t.startLat, startLon = first?.lon ?: t.startLon,
            endLat = last?.lat, endLon = last?.lon,
            startLabel = when { first == null -> ""; sh -> s.homeLabel; else -> labeler(first.lat, first.lon) },
            endLabel = when { last == null -> ""; eh -> s.homeLabel; else -> labeler(last.lat, last.lon) },
            endOdometer = endOdometer ?: t.endOdometer, endOdometerPhoto = endPhoto ?: t.endOdometerPhoto,
            startsAtHome = sh, endsAtHome = eh, needsTaxReview = sh || eh, pausedMs = pausedMs, pausedAt = null,
            updatedAt = now,
        )
        val done = draft.copy(confidence = Ledger.confidence(draft))
        trips.update(done)
        audit.insert(AuditEntry(entityType = "trip", entityId = tripId, field = "status", oldValue = t.status.name, newValue = "COMPLETED",
            reason = "Trip finished: ${"%.2f".format(GeoMath.metersToMiles(done.gpsMeters))} mi verified GPS, ${pts.size} usable fixes"))
        return done
    }

    // ---- Audited edits --------------------------------------------------------------------------

    private suspend fun log(id: Long, field: String, old: Any?, new: Any?, reason: String) {
        if (old?.toString() != new?.toString()) audit.insert(AuditEntry(entityType = "trip", entityId = id, field = field, oldValue = old?.toString(), newValue = new?.toString(), reason = reason))
    }

    suspend fun classify(id: Long, cls: TripClass, purpose: String, businessMiles: Double?, personalMiles: Double?, reason: String = "User classification") {
        val t = trips.get(id) ?: return
        val biz = businessMiles ?: if (cls == TripClass.BUSINESS) Ledger.totalMiles(t).first else 0.0
        val pers = personalMiles ?: if (cls == TripClass.PERSONAL) Ledger.totalMiles(t).first else 0.0
        log(id, "classification", t.classification, cls, reason)
        log(id, "purpose", t.purpose, purpose, reason)
        log(id, "businessMiles", "%.2f".format(t.businessMiles), "%.2f".format(biz), reason)
        log(id, "personalMiles", "%.2f".format(t.personalMiles), "%.2f".format(pers), reason)
        trips.update(t.copy(classification = cls, purpose = purpose, businessMiles = biz, personalMiles = pers, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setOdometer(id: Long, start: Double?, end: Double?, startPhoto: String?, endPhoto: String?) {
        val t = trips.get(id) ?: return
        log(id, "startOdometer", t.startOdometer, start, "Odometer entry")
        log(id, "endOdometer", t.endOdometer, end, "Odometer entry")
        if (startPhoto != t.startOdometerPhoto) log(id, "startOdometerPhoto", t.startOdometerPhoto, startPhoto, "Photo attached")
        if (endPhoto != t.endOdometerPhoto) log(id, "endOdometerPhoto", t.endOdometerPhoto, endPhoto, "Photo attached")
        val u = t.copy(startOdometer = start, endOdometer = end, startOdometerPhoto = startPhoto, endOdometerPhoto = endPhoto, updatedAt = System.currentTimeMillis())
        trips.update(u.copy(confidence = Ledger.confidence(u)))
    }

    suspend fun adjustMiles(id: Long, miles: Double?, reason: String) {
        require(reason.isNotBlank()) { "A reason is required for manual mileage adjustments" }
        val t = trips.get(id) ?: return
        log(id, "adjustedMiles", t.adjustedMiles, miles, reason)
        val u = t.copy(adjustedMiles = miles, updatedAt = System.currentTimeMillis())
        trips.update(u.copy(confidence = Ledger.confidence(u)))
    }

    suspend fun setReviewed(id: Long, deductible: Boolean, reason: String) {
        val t = trips.get(id) ?: return
        log(id, "needsTaxReview", t.needsTaxReview, !deductible, reason)
        trips.update(t.copy(needsTaxReview = !deductible, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setUploadStatus(id: Long, s: UploadStatus) {
        val t = trips.get(id) ?: return
        log(id, "beeUploadStatus", t.beeUploadStatus, s, "Bee Maps upload status")
        trips.update(t.copy(beeUploadStatus = s, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setNotes(id: Long, notes: String) {
        val t = trips.get(id) ?: return
        log(id, "notes", t.notes, notes, "Notes edited")
        trips.update(t.copy(notes = notes, updatedAt = System.currentTimeMillis()))
    }

    suspend fun addManualTrip(vehicleId: Long, start: Long, end: Long, miles: Double?, startOdo: Double?, endOdo: Double?, from: String, to: String, purpose: String, cls: TripClass): Long {
        val t = Trip(vehicleId = vehicleId, status = TripStatus.COMPLETED, source = RecordSource.MANUAL, startTime = start, endTime = end,
            startLabel = from, endLabel = to, startOdometer = startOdo, endOdometer = endOdo, adjustedMiles = miles,
            classification = cls, purpose = purpose,
            businessMiles = 0.0, personalMiles = 0.0)
        val withMiles = t.copy(businessMiles = if (cls == TripClass.BUSINESS) Ledger.totalMiles(t).first else 0.0)
        val id = trips.insert(withMiles.copy(confidence = Ledger.confidence(withMiles)))
        audit.insert(AuditEntry(entityType = "trip", entityId = id, field = "created", oldValue = null, newValue = "manual", reason = "Manual trip entry (no GPS evidence)"))
        return id
    }

    suspend fun delete(id: Long, reason: String) {
        val t = trips.get(id) ?: return
        audit.insert(AuditEntry(entityType = "trip", entityId = id, field = "deleted", oldValue = "${t.startTime}|${"%.2f".format(Ledger.totalMiles(t).first)} mi", newValue = null, reason = reason))
        trips.delete(id)
    }
}
