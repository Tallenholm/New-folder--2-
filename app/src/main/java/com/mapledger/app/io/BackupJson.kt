package com.mapledger.app.io

import com.mapledger.app.data.db.*
import org.json.JSONArray
import org.json.JSONObject

/** Full-fidelity JSON snapshot of the database (used for JSON export and encrypted backups). */
object BackupJson {
    const val FORMAT = "mapledger-backup"
    const val VERSION = 1

    class Snapshot(
        val vehicles: List<Vehicle>, val trips: List<Trip>, val points: List<TrackPoint>, val audit: List<AuditEntry>,
        val expenses: List<Expense>, val payments: List<Payment>, val routes: List<PlannedRoute>, val routePoints: List<RoutePoint>,
        val targets: List<BurstTarget>, val goals: List<HistoricalGoal>, val rates: List<MileageRate>,
    )

    private fun JSONObject.optD(k: String): Double? = if (isNull(k) || !has(k)) null else getDouble(k)
    private fun JSONObject.optL(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)
    private fun JSONObject.optI(k: String): Int? = if (isNull(k) || !has(k)) null else getInt(k)
    private fun JSONObject.optS(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)
    private fun JSONObject.putN(k: String, v: Any?): JSONObject = put(k, v ?: JSONObject.NULL)

    fun encode(s: Snapshot): String {
        val o = JSONObject().put("format", FORMAT).put("version", VERSION).put("schema", AppDatabase.VERSION).put("exportedAt", System.currentTimeMillis())
        o.put("vehicles", JSONArray(s.vehicles.map { v ->
            JSONObject().put("id", v.id).put("name", v.name).putN("year", v.year).put("make", v.make).put("model", v.model).put("trim", v.trim)
                .put("isPrimary", v.isPrimary).put("deductionMethod", v.deductionMethod.name).putN("firstBusinessYear", v.firstBusinessYear)
                .put("isRental", v.isRental).put("active", v.active).put("notes", v.notes)
        }))
        o.put("trips", JSONArray(s.trips.map { t ->
            JSONObject().put("id", t.id).put("vehicleId", t.vehicleId).put("status", t.status.name).put("source", t.source.name)
                .put("startTime", t.startTime).putN("endTime", t.endTime).putN("startLat", t.startLat).putN("startLon", t.startLon)
                .putN("endLat", t.endLat).putN("endLon", t.endLon).put("startLabel", t.startLabel).put("endLabel", t.endLabel)
                .putN("startOdometer", t.startOdometer).putN("endOdometer", t.endOdometer)
                .putN("startOdometerPhoto", t.startOdometerPhoto).putN("endOdometerPhoto", t.endOdometerPhoto)
                .put("gpsMeters", t.gpsMeters).put("gapMeters", t.gapMeters).put("gapCount", t.gapCount).putN("adjustedMiles", t.adjustedMiles)
                .put("movingMs", t.movingMs).put("pausedMs", t.pausedMs).putN("pausedAt", t.pausedAt).put("segmentCount", t.segmentCount)
                .put("classification", t.classification.name).put("businessMiles", t.businessMiles).put("personalMiles", t.personalMiles)
                .put("purpose", t.purpose).put("startsAtHome", t.startsAtHome).put("endsAtHome", t.endsAtHome).put("needsTaxReview", t.needsTaxReview)
                .put("confidence", t.confidence).put("beeUploadStatus", t.beeUploadStatus.name).put("notes", t.notes).putN("importHash", t.importHash)
                .put("createdAt", t.createdAt).put("updatedAt", t.updatedAt)
        }))
        // Points are stored compactly as arrays: [id,tripId,segment,time,lat,lon,acc,alt,speed,bearing,flags]
        o.put("points", JSONArray(s.points.map { p ->
            JSONArray().put(p.id).put(p.tripId).put(p.segment).put(p.time).put(p.lat).put(p.lon).put(p.accuracyM.toDouble())
                .put(p.altitudeM ?: JSONObject.NULL).put(p.speedMps?.toDouble() ?: JSONObject.NULL).put(p.bearing?.toDouble() ?: JSONObject.NULL).put(p.flags)
        }))
        o.put("audit", JSONArray(s.audit.map { a ->
            JSONObject().put("id", a.id).put("entityType", a.entityType).put("entityId", a.entityId).put("time", a.time).put("field", a.field)
                .putN("oldValue", a.oldValue).putN("newValue", a.newValue).put("reason", a.reason)
        }))
        o.put("expenses", JSONArray(s.expenses.map { e ->
            JSONObject().put("id", e.id).putN("vehicleId", e.vehicleId).put("date", e.date).put("category", e.category.name).put("amountCents", e.amountCents)
                .put("vendor", e.vendor).put("businessPct", e.businessPct).putN("gallons", e.gallons).putN("odometer", e.odometer)
                .putN("receiptPhoto", e.receiptPhoto).put("notes", e.notes).put("createdAt", e.createdAt)
        }))
        o.put("payments", JSONArray(s.payments.map { p ->
            JSONObject().put("id", p.id).put("date", p.date).put("kind", p.kind.name).put("currency", p.currency.name).put("amountMicros", p.amountMicros)
                .putN("usdFmvCents", p.usdFmvCents).put("status", p.status.name).putN("expectedUsdCents", p.expectedUsdCents).put("campaignId", p.campaignId)
                .putN("burstTargetId", p.burstTargetId).put("txHash", p.txHash).put("notes", p.notes).putN("importHash", p.importHash).put("createdAt", p.createdAt)
        }))
        o.put("routes", JSONArray(s.routes.map { r ->
            JSONObject().put("id", r.id).put("name", r.name).put("importedAt", r.importedAt).put("sourceFile", r.sourceFile)
                .put("lengthMeters", r.lengthMeters).put("pointCount", r.pointCount).put("contentHash", r.contentHash)
        }))
        o.put("routePoints", JSONArray(s.routePoints.map { p ->
            JSONArray().put(p.id).put(p.routeId).put(p.seq).put(p.lat).put(p.lon).put(p.coveredByTripId ?: JSONObject.NULL)
        }))
        o.put("targets", JSONArray(s.targets.map { t ->
            JSONObject().put("id", t.id).put("name", t.name).put("lat", t.lat).put("lon", t.lon).put("radiusM", t.radiusM).put("campaignId", t.campaignId)
                .putN("expectedRewardCents", t.expectedRewardCents).put("status", t.status.name).putN("firstPassedTripId", t.firstPassedTripId)
                .putN("firstPassedTime", t.firstPassedTime).put("passCount", t.passCount).put("notes", t.notes)
        }))
        o.put("goals", JSONArray(s.goals.map { g ->
            JSONObject().put("id", g.id).put("weekStart", g.weekStart).put("distanceKm", g.distanceKm).put("source", g.source)
                .putN("evidencePhoto", g.evidencePhoto).put("notes", g.notes)
        }))
        o.put("rates", JSONArray(s.rates.map { r ->
            JSONObject().put("id", r.id).put("fromEpochDay", r.fromEpochDay).put("toEpochDay", r.toEpochDay).put("tenthsOfCent", r.tenthsOfCent).put("label", r.label)
        }))
        return o.toString()
    }

    private inline fun <T> JSONArray?.mapObj(f: (JSONObject) -> T): List<T> = if (this == null) emptyList() else (0 until length()).map { f(getJSONObject(it)) }
    private inline fun <T> JSONArray?.mapArr(f: (JSONArray) -> T): List<T> = if (this == null) emptyList() else (0 until length()).map { f(getJSONArray(it)) }
    private fun JSONArray.dOrNull(i: Int): Double? = if (isNull(i)) null else getDouble(i)

    fun decode(json: String): Snapshot {
        val o = JSONObject(json)
        require(o.optString("format") == FORMAT) { "Not a MapLedger backup" }
        require(o.optInt("version") <= VERSION) { "Backup was made by a newer MapLedger version" }
        return Snapshot(
            vehicles = o.optJSONArray("vehicles").mapObj { Vehicle(it.getLong("id"), it.getString("name"), it.optI("year"), it.getString("make"), it.getString("model"),
                it.optString("trim"), it.getBoolean("isPrimary"), DeductionMethod.valueOf(it.getString("deductionMethod")), it.optI("firstBusinessYear"),
                it.optBoolean("isRental"), it.optBoolean("active", true), it.optString("notes")) },
            trips = o.optJSONArray("trips").mapObj { Trip(
                id = it.getLong("id"), vehicleId = it.getLong("vehicleId"), status = TripStatus.valueOf(it.getString("status")), source = RecordSource.valueOf(it.getString("source")),
                startTime = it.getLong("startTime"), endTime = it.optL("endTime"), startLat = it.optD("startLat"), startLon = it.optD("startLon"),
                endLat = it.optD("endLat"), endLon = it.optD("endLon"), startLabel = it.optString("startLabel"), endLabel = it.optString("endLabel"),
                startOdometer = it.optD("startOdometer"), endOdometer = it.optD("endOdometer"), startOdometerPhoto = it.optS("startOdometerPhoto"), endOdometerPhoto = it.optS("endOdometerPhoto"),
                gpsMeters = it.getDouble("gpsMeters"), gapMeters = it.getDouble("gapMeters"), gapCount = it.optInt("gapCount"), adjustedMiles = it.optD("adjustedMiles"),
                movingMs = it.getLong("movingMs"), pausedMs = it.getLong("pausedMs"), pausedAt = it.optL("pausedAt"), segmentCount = it.optInt("segmentCount", 1),
                classification = TripClass.valueOf(it.getString("classification")), businessMiles = it.getDouble("businessMiles"), personalMiles = it.getDouble("personalMiles"),
                purpose = it.optString("purpose"), startsAtHome = it.optBoolean("startsAtHome"), endsAtHome = it.optBoolean("endsAtHome"), needsTaxReview = it.optBoolean("needsTaxReview"),
                confidence = it.optInt("confidence"), beeUploadStatus = UploadStatus.valueOf(it.optString("beeUploadStatus", "UNKNOWN")), notes = it.optString("notes"),
                importHash = it.optS("importHash"), createdAt = it.getLong("createdAt"), updatedAt = it.getLong("updatedAt")) },
            points = o.optJSONArray("points").mapArr { TrackPoint(it.getLong(0), it.getLong(1), it.getInt(2), it.getLong(3), it.getDouble(4), it.getDouble(5),
                it.getDouble(6).toFloat(), it.dOrNull(7), it.dOrNull(8)?.toFloat(), it.dOrNull(9)?.toFloat(), it.getInt(10)) },
            audit = o.optJSONArray("audit").mapObj { AuditEntry(it.getLong("id"), it.getString("entityType"), it.getLong("entityId"), it.getLong("time"), it.getString("field"),
                it.optS("oldValue"), it.optS("newValue"), it.optString("reason")) },
            expenses = o.optJSONArray("expenses").mapObj { Expense(it.getLong("id"), it.optL("vehicleId"), it.getLong("date"), ExpenseCategory.valueOf(it.getString("category")),
                it.getLong("amountCents"), it.optString("vendor"), it.optInt("businessPct", 100), it.optD("gallons"), it.optD("odometer"), it.optS("receiptPhoto"),
                it.optString("notes"), it.getLong("createdAt")) },
            payments = o.optJSONArray("payments").mapObj { Payment(it.getLong("id"), it.getLong("date"), PaymentKind.valueOf(it.getString("kind")),
                PaymentCurrency.valueOf(it.getString("currency")), it.getLong("amountMicros"), it.optL("usdFmvCents"), PaymentStatus.valueOf(it.getString("status")),
                it.optL("expectedUsdCents"), it.optString("campaignId"), it.optL("burstTargetId"), it.optString("txHash"), it.optString("notes"), it.optS("importHash"), it.getLong("createdAt")) },
            routes = o.optJSONArray("routes").mapObj { PlannedRoute(it.getLong("id"), it.getString("name"), it.getLong("importedAt"), it.optString("sourceFile"),
                it.getDouble("lengthMeters"), it.getInt("pointCount"), it.optString("contentHash")) },
            routePoints = o.optJSONArray("routePoints").mapArr { RoutePoint(it.getLong(0), it.getLong(1), it.getInt(2), it.getDouble(3), it.getDouble(4), if (it.isNull(5)) null else it.getLong(5)) },
            targets = o.optJSONArray("targets").mapObj { BurstTarget(it.getLong("id"), it.getString("name"), it.getDouble("lat"), it.getDouble("lon"), it.getDouble("radiusM"),
                it.optString("campaignId"), it.optL("expectedRewardCents"), BurstStatus.valueOf(it.getString("status")), it.optL("firstPassedTripId"),
                it.optL("firstPassedTime"), it.optInt("passCount"), it.optString("notes")) },
            goals = o.optJSONArray("goals").mapObj { HistoricalGoal(it.getLong("id"), it.getLong("weekStart"), it.getDouble("distanceKm"), it.optString("source"),
                it.optS("evidencePhoto"), it.optString("notes")) },
            rates = o.optJSONArray("rates").mapObj { MileageRate(it.getLong("id"), it.getLong("fromEpochDay"), it.getLong("toEpochDay"), it.getInt("tenthsOfCent"), it.getString("label")) },
        )
    }
}
