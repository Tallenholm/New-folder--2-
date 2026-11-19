package com.mapledger.app.data.repo

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.room.withTransaction
import com.mapledger.app.data.db.*
import com.mapledger.app.domain.GeoMath
import com.mapledger.app.domain.Importers
import com.mapledger.app.domain.LatLon
import com.mapledger.app.domain.Ledger
import com.mapledger.app.domain.FixFlag
import com.mapledger.app.domain.DistanceEngine
import com.mapledger.app.domain.RawFix
import com.mapledger.app.io.BackupCrypto
import com.mapledger.app.io.BackupJson
import com.mapledger.app.io.Csv
import com.mapledger.app.io.Gpx
import com.mapledger.app.io.GpxPoint
import com.mapledger.app.io.Timeline
import com.mapledger.app.io.Xlsx
import com.mapledger.app.util.Hash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ImportResult(val imported: Int, val skipped: Int, val messages: List<String>) {
    val summary get() = "Imported $imported, skipped $skipped" + if (messages.isNotEmpty()) "\n" + messages.take(8).joinToString("\n") else ""
}

class TransferRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val finance: FinanceRepository,
    private val bee: BeeRepository,
    private val settings: SettingsRepository,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private fun read(uri: Uri): ByteArray = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot open file")
    private fun name(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/') ?: "file"

    /** Whole-file duplicate guard: the same bytes can never be imported twice. */
    private suspend fun guard(type: String, bytes: ByteArray): String? {
        val h = Hash.sha256(bytes)
        return if (db.imports().countByHash("$type|$h") > 0) null else "$type|$h"
    }
    private suspend fun record(type: String, key: String, file: String, ok: Int, skip: Int) =
        db.imports().insert(ImportRecord(type = type, fileName = file, contentHash = key, rowsImported = ok, rowsSkipped = skip))

    private val dupFile = ImportResult(0, 0, listOf("This exact file was already imported — nothing changed."))

    // ---------------------------------------------------------------- Imports

    enum class GpxMode { PLANNED_ROUTE, DRIVEN_TRIP, BURST_TARGETS }

    suspend fun importGpx(uri: Uri, mode: GpxMode, vehicleId: Long?): ImportResult = withContext(Dispatchers.IO) {
        val bytes = read(uri)
        val key = guard("gpx-$mode", bytes) ?: return@withContext dupFile
        val gpx = Gpx.parse(ByteArrayInputStream(bytes))
        val file = name(uri)
        var ok = 0; var skip = 0; val msgs = mutableListOf<String>()
        when (mode) {
            GpxMode.PLANNED_ROUTE -> {
                val lines = gpx.routes.map { it.name to it.points } + gpx.tracks.map { t -> t.name to t.segments.flatten() }
                lines.forEach { (n, pts) ->
                    val id = bee.addRoute(n, file, pts.map { LatLon(it.lat, it.lon) }, Hash.sha256(pts.joinToString { "${it.lat},${it.lon}" }))
                    if (id != null) ok++ else { skip++; msgs += "Route '$n' already imported or too short" }
                }
            }
            GpxMode.DRIVEN_TRIP -> {
                val vid = vehicleId ?: error("Select a vehicle first")
                gpx.tracks.forEach { trk ->
                    val pts = trk.segments.flatMapIndexed { si, seg -> seg.map { si to it } }
                    if (pts.any { it.second.time == null }) { skip++; msgs += "Track '${trk.name}' has no timestamps — cannot verify it was driven"; return@forEach }
                    val h = Hash.sha256("gpxtrip|" + pts.joinToString { "${it.second.time},${it.second.lat},${it.second.lon}" })
                    if (db.trips().countByHash(h) > 0) { skip++; msgs += "Track '${trk.name}' already imported"; return@forEach }
                    insertTrackTrip(vid, RecordSource.GPX_IMPORT, pts.map { (s, p) -> RawFix(p.time!!, p.lat, p.lon, 10f, null, s) }, h, "GPX: ${trk.name} ($file)")
                    ok++
                }
            }
            GpxMode.BURST_TARGETS -> {
                gpx.waypoints.forEach { w ->
                    bee.saveTarget(BurstTarget(name = w.name ?: "Target", lat = w.lat, lon = w.lon, notes = w.desc ?: "Imported from $file")); ok++
                }
                if (gpx.waypoints.isEmpty()) msgs += "No <wpt> waypoints found"
            }
        }
        record("gpx-$mode", key, file, ok, skip)
        ImportResult(ok, skip, msgs)
    }

    /** Creates a COMPLETED trip from timestamped points, running them through the same distance engine as live GPS. */
    private suspend fun insertTrackTrip(vehicleId: Long, source: RecordSource, fixes: List<RawFix>, hash: String, note: String, googleMeters: Double? = null): Long {
        val sorted = fixes.sortedBy { it.time }
        val cfg = com.mapledger.app.domain.EngineConfig(gapTimeMs = if (source == RecordSource.TIMELINE_IMPORT) 15 * 60_000L else 45_000L,
            gapTrustMeters = if (source == RecordSource.TIMELINE_IMPORT) 3_000.0 else 250.0)
        val (engine, flags) = DistanceEngine.replay(sorted, cfg)
        val snap = engine.snapshot()
        val s = settings.current()
        fun home(lat: Double, lon: Double) = s.homeLat != null && s.homeLon != null && GeoMath.haversineMeters(lat, lon, s.homeLat, s.homeLon) <= s.homeRadiusM
        val first = sorted.first(); val last = sorted.last()
        val t = Trip(vehicleId = vehicleId, status = TripStatus.COMPLETED, source = source, startTime = first.time, endTime = last.time,
            startLat = first.lat, startLon = first.lon, endLat = last.lat, endLon = last.lon,
            startLabel = if (home(first.lat, first.lon)) s.homeLabel else "%.4f, %.4f".format(first.lat, first.lon),
            endLabel = if (home(last.lat, last.lon)) s.homeLabel else "%.4f, %.4f".format(last.lat, last.lon),
            gpsMeters = snap.verifiedMeters, gapMeters = snap.gapStraightLineMeters, gapCount = snap.gaps.size, movingMs = snap.movingMs,
            startsAtHome = home(first.lat, first.lon), endsAtHome = home(last.lat, last.lon),
            needsTaxReview = home(first.lat, first.lon) || home(last.lat, last.lon),
            notes = note + (googleMeters?.let { "\nGoogle's own distance estimate: %.2f mi (not used)".format(GeoMath.metersToMiles(it)) } ?: ""),
            importHash = hash)
        return db.withTransaction {
            val id = db.trips().insert(t.copy(confidence = Ledger.confidence(t)))
            db.points().insertAll(sorted.mapIndexed { i, f -> TrackPoint(tripId = id, segment = f.segment, time = f.time, lat = f.lat, lon = f.lon, accuracyM = f.accuracyM, flags = flags[i]) })
            db.audit().insert(AuditEntry(entityType = "trip", entityId = id, field = "created", oldValue = null, newValue = source.name, reason = note))
            id
        }.also { bee.analyzeTrip(it) }
    }

    suspend fun importTimeline(uri: Uri, vehicleId: Long): ImportResult = withContext(Dispatchers.IO) {
        val bytes = read(uri)
        val key = guard("timeline", bytes) ?: return@withContext dupFile
        val drives = Timeline.parse(String(bytes, Charsets.UTF_8))
        var ok = 0; var skip = 0; val msgs = mutableListOf<String>()
        drives.forEach { d ->
            val pts = d.points.filter { it.time != null }
            if (pts.size < 2) { skip++; msgs += "Drive at ${Instant.ofEpochMilli(d.startTime)} has too few points"; return@forEach }
            val h = Hash.sha256("timeline|${d.startTime}|${d.endTime}")
            if (db.trips().countByHash(h) > 0) { skip++; return@forEach }
            insertTrackTrip(vehicleId, RecordSource.TIMELINE_IMPORT, pts.map { RawFix(it.time!!, it.lat, it.lon, 20f) }, h,
                "Google Timeline (${d.activityType}) — sparse points, review before claiming", d.googleDistanceMeters)
            ok++
        }
        if (drives.isEmpty()) msgs += "No driving segments found in this file"
        record("timeline", key, name(uri), ok, skip)
        ImportResult(ok, skip, msgs)
    }

    private fun rowsOf(bytes: ByteArray): List<List<String>> =
        if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) Xlsx.readFirstSheet(ByteArrayInputStream(bytes))
        else Csv.parse(String(bytes, Charsets.UTF_8))

    suspend fun importPayments(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val bytes = read(uri)
        val key = guard("payments", bytes) ?: return@withContext dupFile
        val res = Importers.payments(rowsOf(bytes), zone)
        var ok = 0; var dup = 0
        res.items.forEach { p -> if (p.importHash != null && db.payments().countByHash(p.importHash) > 0) dup++ else { db.payments().insert(p); ok++ } }
        val msgs = res.skipped + if (dup > 0) listOf("$dup duplicate transactions already in MapLedger") else emptyList()
        record("payments", key, name(uri), ok, res.skipped.size + dup)
        ImportResult(ok, res.skipped.size + dup, msgs)
    }

    suspend fun importLedger(uri: Uri, vehicleId: Long): ImportResult = withContext(Dispatchers.IO) {
        val bytes = read(uri)
        val key = guard("ledger", bytes) ?: return@withContext dupFile
        val res = Importers.ledger(rowsOf(bytes), zone)
        var ok = 0; var dup = 0
        res.items.forEach { r ->
            if (db.trips().countByHash(r.hash) > 0) { dup++; return@forEach }
            val cls = when {
                r.businessMiles != null && r.businessMiles > 0 && (r.personalMiles ?: 0.0) > 0 -> TripClass.MIXED
                r.businessMiles != null && r.businessMiles > 0 -> TripClass.BUSINESS
                r.personalMiles != null && r.personalMiles > 0 -> TripClass.PERSONAL
                else -> TripClass.UNCLASSIFIED
            }
            val t = Trip(vehicleId = vehicleId, status = TripStatus.COMPLETED, source = RecordSource.CSV_IMPORT, startTime = r.date, endTime = r.date,
                startLabel = r.from, endLabel = r.to, startOdometer = r.startOdo, endOdometer = r.endOdo,
                adjustedMiles = if (r.startOdo != null && r.endOdo != null) null else r.totalMiles,
                classification = cls, businessMiles = r.businessMiles ?: 0.0, personalMiles = r.personalMiles ?: 0.0, purpose = r.purpose,
                notes = "Imported from ledger ${name(uri)} — no GPS evidence", importHash = r.hash)
            val id = db.trips().insert(t.copy(confidence = Ledger.confidence(t)))
            db.audit().insert(AuditEntry(entityType = "trip", entityId = id, field = "created", oldValue = null, newValue = "CSV_IMPORT", reason = "Ledger import"))
            ok++
        }
        val msgs = res.skipped + if (dup > 0) listOf("$dup rows already imported") else emptyList()
        record("ledger", key, name(uri), ok, res.skipped.size + dup)
        ImportResult(ok, res.skipped.size + dup, msgs)
    }

    /** CSV columns: name, lat, lon, [radius_m], [campaign], [expected_usd] */
    suspend fun importTargetsCsv(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val bytes = read(uri)
        val key = guard("targets", bytes) ?: return@withContext dupFile
        val rows = rowsOf(bytes)
        if (rows.isEmpty()) return@withContext ImportResult(0, 0, listOf("Empty file"))
        val h = rows.first().map { it.lowercase().trim() }
        fun idx(vararg n: String) = h.indexOfFirst { c -> n.any { c.contains(it) } }
        val iN = idx("name", "target"); val iLat = idx("lat"); val iLon = idx("lon", "lng"); val iR = idx("radius")
        val iC = idx("campaign"); val iU = idx("usd", "reward", "expected")
        if (iLat < 0 || iLon < 0) return@withContext ImportResult(0, rows.size - 1, listOf("Need lat and lon columns"))
        var ok = 0; var skip = 0
        rows.drop(1).forEach { r ->
            fun g(i: Int) = if (i in r.indices) r[i].trim() else ""
            val lat = g(iLat).toDoubleOrNull(); val lon = g(iLon).toDoubleOrNull()
            if (lat == null || lon == null) { skip++; return@forEach }
            bee.saveTarget(BurstTarget(name = g(iN).ifBlank { "Target" }, lat = lat, lon = lon, radiusM = g(iR).toDoubleOrNull() ?: 75.0,
                campaignId = g(iC), expectedRewardCents = Importers.parseMoney(g(iU))?.let { (it * 100).toLong() }))
            ok++
        }
        record("targets", key, name(uri), ok, skip)
        ImportResult(ok, skip, emptyList())
    }

    // ---------------------------------------------------------------- Exports

    private fun write(uri: Uri, block: (java.io.OutputStream) -> Unit) {
        context.contentResolver.openOutputStream(uri, "wt")?.use(block) ?: error("Cannot write file")
    }

    private fun fmtDate(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(dateFmt)
    private fun fmtTime(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).atZone(zone).format(timeFmt) } ?: ""

    val IRS_HEADER = listOf("Date", "Start time", "End time", "Start location", "End location", "Start odometer", "End odometer",
        "Total miles", "Miles basis", "Business miles", "Personal miles", "Classification", "Business purpose", "Vehicle",
        "Home travel (review)", "Verified GPS miles", "Unverified gap miles", "GPS fixes", "Start coords", "End coords",
        "Source", "Confidence", "IRS rate $/mi", "Mileage deduction $", "Manual adjustments", "Trip ID")

    private suspend fun irsRows(year: Int): List<List<Any>> {
        val (from, to) = Ledger.yearBounds(year, zone)
        val trips = db.trips().completedBetween(from, to)
        val vehicles = db.vehicles().all().associateBy { it.id }
        val rates = finance.rates()
        val audit = db.audit().all().filter { it.entityType == "trip" }.groupBy { it.entityId }
        return trips.map { t ->
            val (total, basis) = Ledger.totalMiles(t)
            val biz = if (t.needsTaxReview) 0.0 else Ledger.businessMiles(t)
            val d = Ledger.localDate(t.startTime, zone)
            val rate = com.mapledger.app.domain.TaxMath.rateFor(d, rates)
            val v = vehicles[t.vehicleId]
            val adj = audit[t.id].orEmpty().filter { it.field in setOf("adjustedMiles", "businessMiles", "classification", "startOdometer", "endOdometer") }
            listOf(fmtDate(t.startTime), fmtTime(t.startTime), fmtTime(t.endTime), t.startLabel, t.endLabel,
                t.startOdometer ?: "", t.endOdometer ?: "", round2(total), basis.name, round2(biz), round2(Ledger.personalMiles(t)),
                t.classification.name, t.purpose, v?.let { "${it.year ?: ""} ${it.make} ${it.model} ${it.trim}".trim() } ?: "",
                if (t.needsTaxReview) "YES — not counted until reviewed" else if (t.startsAtHome || t.endsAtHome) "Reviewed" else "",
                round2(GeoMath.metersToMiles(t.gpsMeters)), round2(GeoMath.metersToMiles(t.gapMeters)), db.points().count(t.id),
                t.startLat?.let { "%.5f,%.5f".format(it, t.startLon) } ?: "", t.endLat?.let { "%.5f,%.5f".format(it, t.endLon) } ?: "",
                t.source.name, t.confidence, rate?.dollarsPerMile ?: "", rate?.let { round2(biz * it.dollarsPerMile) } ?: "",
                adj.joinToString("; ") { "${it.field}: ${it.oldValue} → ${it.newValue} (${it.reason})" }, t.id)
        }
    }
    private fun round2(d: Double) = Math.round(d * 100) / 100.0

    suspend fun exportIrsCsv(uri: Uri, year: Int) = withContext(Dispatchers.IO) {
        val rows = irsRows(year)
        write(uri) { os -> os.writer().let { Csv.write(it, listOf(IRS_HEADER) + rows.map { r -> r.map(Any::toString) }) } }
        rows.size
    }

    suspend fun exportXlsx(uri: Uri, year: Int) = withContext(Dispatchers.IO) {
        val rows = irsRows(year)
        fun cell(a: Any) = when (a) { is Number -> Xlsx.num(a.toDouble()); else -> Xlsx.text(a.toString()) }
        val (from, to) = Ledger.yearBounds(year, zone)
        val exp = db.expenses().all().filter { it.date in from until to }
        val pay = db.payments().all().filter { it.date in from until to }
        val veh = db.vehicles().all().associateBy { it.id }
        val sheets = listOf(
            Xlsx.Sheet("Mileage log $year", listOf(IRS_HEADER.map(Xlsx::text)) + rows.map { r -> r.map(::cell) }),
            Xlsx.Sheet("Expenses $year", listOf(listOf("Date", "Category", "Vendor", "Amount $", "Business %", "Vehicle", "Gallons", "Odometer", "Receipt", "Notes").map(Xlsx::text)) +
                exp.map { e -> listOf(Xlsx.text(fmtDate(e.date)), Xlsx.text(e.category.label), Xlsx.text(e.vendor), Xlsx.num(e.amountCents / 100.0), Xlsx.num(e.businessPct.toDouble()),
                    Xlsx.text(e.vehicleId?.let { veh[it]?.name } ?: ""), e.gallons?.let(Xlsx::num) ?: Xlsx.text(""), e.odometer?.let(Xlsx::num) ?: Xlsx.text(""),
                    Xlsx.text(if (e.receiptPhoto != null) "yes" else "MISSING"), Xlsx.text(e.notes)) }),
            Xlsx.Sheet("Income $year", listOf(listOf("Date", "Kind", "Currency", "Amount", "USD FMV $", "Campaign", "Tx", "Notes").map(Xlsx::text)) +
                pay.map { p -> listOf(Xlsx.text(fmtDate(p.date)), Xlsx.text(p.kind.name), Xlsx.text(p.currency.name), Xlsx.num(p.amountMicros / 1e6),
                    p.usdFmvCents?.let { Xlsx.num(it / 100.0) } ?: Xlsx.text("MISSING"), Xlsx.text(p.campaignId), Xlsx.text(p.txHash), Xlsx.text(p.notes)) }),
        )
        write(uri) { Xlsx.write(it, sheets) }
        rows.size
    }

    suspend fun exportTripsGpx(uri: Uri, tripIds: List<Long>) = withContext(Dispatchers.IO) {
        val tracks = tripIds.mapNotNull { id ->
            val t = db.trips().get(id) ?: return@mapNotNull null
            val pts = db.points().forTrip(id).filter { it.flags != FixFlag.LOW_ACCURACY && it.flags != FixFlag.DUPLICATE && it.flags != FixFlag.IMPOSSIBLE_SPEED }
            // Start a new <trkseg> at every pause and every GPS gap so no unsupported line is drawn.
            val segs = mutableListOf<MutableList<GpxPoint>>()
            var lastSeg = -1
            pts.forEach { p ->
                if (p.segment != lastSeg || p.flags == FixFlag.GAP_BEFORE || segs.isEmpty()) segs += mutableListOf<GpxPoint>()
                segs.last() += GpxPoint(p.lat, p.lon, p.time, p.altitudeM); lastSeg = p.segment
            }
            Gpx.ExportTrack("MapLedger trip ${fmtDate(t.startTime)} ${fmtTime(t.startTime)}", segs)
        }
        write(uri) { os -> Gpx.write(os.writer(), tracks) }
        tracks.size
    }

    suspend fun snapshot(): BackupJson.Snapshot = BackupJson.Snapshot(
        db.vehicles().all(), db.trips().all(), db.points().all(), db.audit().all(), db.expenses().all(), db.payments().all(),
        db.bee().routes(), db.bee().allRoutePoints(), db.bee().targets(), db.bee().goals(), db.rates().all())

    suspend fun exportJson(uri: Uri) = withContext(Dispatchers.IO) { val s = BackupJson.encode(snapshot()); write(uri) { it.write(s.toByteArray()) } }

    suspend fun backup(uri: Uri, pass: CharArray) = withContext(Dispatchers.Default) {
        val blob = BackupCrypto.encrypt(BackupJson.encode(snapshot()).toByteArray(), pass)
        withContext(Dispatchers.IO) { write(uri) { it.write(blob) } }
    }

    /** Replaces ALL local data with the backup contents, inside one transaction. */
    suspend fun restore(uri: Uri, pass: CharArray) = withContext(Dispatchers.IO) {
        val json = String(BackupCrypto.decrypt(read(uri), pass), Charsets.UTF_8)
        val s = BackupJson.decode(json)
        db.withTransaction {
            db.points().deleteAll(); db.trips().deleteAll(); db.expenses().deleteAll(); db.payments().deleteAll()
            db.bee().deleteAllRoutes(); db.bee().deleteAllTargets(); db.bee().deleteAllGoals(); db.rates().deleteAll(); db.audit().deleteAll()
            db.vehicles().deleteAll()
            db.vehicles().insertAll(s.vehicles); db.trips().insertAll(s.trips); s.points.chunked(2000).forEach { db.points().insertAll(it) }
            db.audit().insertAll(s.audit); db.expenses().insertAll(s.expenses); db.payments().insertAll(s.payments)
            db.bee().insertRoutesRaw(s.routes); s.routePoints.chunked(2000).forEach { db.bee().insertRoutePoints(it) }
            db.bee().insertTargetsRaw(s.targets); db.bee().insertGoals(s.goals); db.rates().insertAll(s.rates)
            db.audit().insert(AuditEntry(entityType = "system", entityId = 0, field = "restore", oldValue = null, newValue = "${s.trips.size} trips", reason = "Restored from encrypted backup"))
        }
        s.trips.size
    }

    /** IRS-style mileage log + annual summary as a printable PDF (US Letter, landscape). */
    suspend fun exportPdf(uri: Uri, year: Int) = withContext(Dispatchers.IO) {
        val rows = irsRows(year)
        val s = settings.current()
        val summary = Ledger.taxYear(year, db.trips().all().filter { it.status == TripStatus.COMPLETED }, db.expenses().all(), db.payments().all(),
            db.vehicles().all(), finance.rates(), s.marginalTaxRate, zone)
        val doc = PdfDocument()
        val W = 792; val H = 612; val M = 28f
        val title = Paint().apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val body = Paint().apply { textSize = 8f; isAntiAlias = true }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val line = Paint().apply { strokeWidth = 0.5f; color = 0xFF999999.toInt() }
        var pageNo = 0
        fun newPage(): PdfDocument.Page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create())

        // Page 1: summary
        var page = newPage(); var c = page.canvas; var y = M + 14
        c.drawText("MapLedger — $year Vehicle Mileage Log & Summary", M, y, title); y += 18
        fun ln(t: String, p: Paint = body) { c.drawText(t, M, y, p); y += 12 }
        ln("Generated ${fmtDate(System.currentTimeMillis())}. Home base: ${s.homeLabel}. Figures are estimates for your tax preparer, not tax advice.")
        y += 6
        ln("Total vehicle miles: %.1f   Business (claimable): %.1f   Personal: %.1f   Unclassified: %.1f".format(summary.totalMiles, summary.businessMiles, summary.personalMiles, summary.unclassifiedMiles), bold)
        ln("Home-travel miles flagged for review (not counted): %.1f   Trips with home start/end: %.1f mi".format(summary.reviewMiles, summary.homeTravelMiles))
        summary.vehicles.forEach { v ->
            ln("${v.vehicle.name} [${v.vehicle.deductionMethod}]: %.1f total mi, %.1f business mi, standard $%.2f, actual-expense alt. $%.2f, claimed $%.2f".format(
                v.totalMiles, v.businessMiles, v.standardDeductionCents / 100.0, v.actualDeductionCents / 100.0, v.claimedCents / 100.0))
            ln("    ${v.methodNote}")
        }
        ln("Other business expenses: $%.2f   Income (USD FMV): $%.2f   Net profit: $%.2f".format(summary.otherDeductionCents / 100.0, summary.incomeCents / 100.0, summary.netProfitCents / 100.0), bold)
        ln("Estimated SE tax: $%.2f   Estimated income tax @ %.0f%%: $%.2f".format(summary.seTaxCents / 100.0, s.marginalTaxRate * 100, summary.incomeTaxCents / 100.0))
        y += 6
        ln("Open evidence items (${summary.missing.size}):", bold)
        summary.missing.take(25).forEach { ln("• ${it.message}") }
        doc.finishPage(page)

        // Log pages
        val cols = listOf("Date" to 46f, "Start" to 28f, "End" to 28f, "From" to 92f, "To" to 92f, "Odo start" to 40f, "Odo end" to 40f,
            "Total mi" to 34f, "Bus. mi" to 32f, "Pers. mi" to 32f, "Class" to 52f, "Purpose" to 130f, "Src/Conf" to 62f, "Deduction" to 40f)
        val idx = listOf(0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 20, 23)
        fun header() {
            page = newPage(); c = page.canvas; y = M + 12
            c.drawText("Mileage log $year (page ${pageNo - 1})", M, y, title.apply { textSize = 12f }); y += 14
            var x = M; cols.forEach { (h, w) -> c.drawText(h, x, y, bold); x += w }; y += 4; c.drawLine(M, y, W - M, y, line); y += 10
        }
        header()
        rows.forEach { r ->
            if (y > H - M) { doc.finishPage(page); header() }
            var x = M
            idx.forEachIndexed { i, ci ->
                val raw = if (ci == 20) "${r[20]}/${r[21]}" else r[ci].toString()
                val w = cols[i].second
                c.drawText(ellipsize(raw, body, w - 3), x, y, body); x += w
            }
            y += 11
        }
        doc.finishPage(page)
        write(uri) { doc.writeTo(it) }
        doc.close()
        rows.size
    }

    private fun ellipsize(s: String, p: Paint, w: Float): String {
        if (p.measureText(s) <= w) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > w) t = t.dropLast(1)
        return "$t…"
    }
}
