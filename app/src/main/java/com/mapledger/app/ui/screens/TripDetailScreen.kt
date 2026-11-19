package com.mapledger.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapledger.app.AppContainer
import com.mapledger.app.data.db.BurstStatus
import com.mapledger.app.data.db.Trip
import com.mapledger.app.data.db.TripClass
import com.mapledger.app.data.db.UploadStatus
import com.mapledger.app.domain.DistanceEngine
import com.mapledger.app.domain.FixFlag
import com.mapledger.app.domain.GeoMath
import com.mapledger.app.domain.LatLon
import com.mapledger.app.domain.Ledger
import com.mapledger.app.domain.RawFix
import com.mapledger.app.domain.TaxMath
import com.mapledger.app.tracking.TrackingService
import com.mapledger.app.ui.appViewModel
import com.mapledger.app.ui.components.*
import com.mapledger.app.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TripDetailViewModel(private val c: AppContainer, val id: Long) : ViewModel() {
    val trip = c.trips.observeTrip(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val points = c.trips.observePoints(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val path = c.trips.observePoints(id).map { pts -> TrackingService.buildPath(pts.map { Triple(LatLon(it.lat, it.lon), it.segment, it.flags) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val audit = c.trips.observeAudit(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val targets = c.bee.observeTargets().map { list -> list.filter { it.firstPassedTripId == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val vehicles = c.finance.observeVehicles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var rateTenths by mutableStateOf<Int?>(null); private set
    var message by mutableStateOf<String?>(null)

    init { viewModelScope.launch { c.trips.get(id)?.let { rateTenths = TaxMath.rateFor(Ledger.localDate(it.startTime), c.finance.rates())?.tenthsOfCent } } }

    fun classify(cls: TripClass, purpose: String, biz: Double?, pers: Double?) = viewModelScope.launch { c.trips.classify(id, cls, purpose, biz, pers) }
    fun odometer(s: Double?, e: Double?, sp: String?, ep: String?) = viewModelScope.launch { c.trips.setOdometer(id, s, e, sp, ep) }
    fun adjust(miles: Double?, reason: String) = viewModelScope.launch { runCatching { c.trips.adjustMiles(id, miles, reason) }.onFailure { message = it.message } }
    fun review(deductible: Boolean, reason: String) = viewModelScope.launch { c.trips.setReviewed(id, deductible, reason) }
    fun upload(s: UploadStatus) = viewModelScope.launch { c.trips.setUploadStatus(id, s) }
    fun notes(n: String) = viewModelScope.launch { c.trips.setNotes(id, n) }
    fun delete(reason: String, done: () -> Unit) = viewModelScope.launch { c.trips.delete(id, reason); done() }
    fun exportGpx(uri: Uri) = viewModelScope.launch { runCatching { c.transfer.exportTripsGpx(uri, listOf(id)) }.onSuccess { message = "GPX exported" }.onFailure { message = it.message } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(id: Long, onBack: () -> Unit) {
    val vm = appViewModel("trip-$id") { TripDetailViewModel(it, id) }
    val trip by vm.trip.collectAsState()
    val path by vm.path.collectAsState()
    val points by vm.points.collectAsState()
    val audit by vm.audit.collectAsState()
    val passed by vm.targets.collectAsState()
    val vehicles by vm.vehicles.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    val gpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { it?.let(vm::exportGpx) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(trip?.let { Fmt.date(it.startTime) } ?: "Trip") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { gpx.launch("mapledger-trip-$id.gpx") }) { Icon(Icons.Default.FileDownload, "Export GPX") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
            })
    }) { pad ->
        val t = trip ?: run { Box(Modifier.padding(pad)) { EmptyState("Loading…") }; return@Scaffold }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrackMap(path, Modifier.fillMaxWidth().height(280.dp), targets = passed.map { MapTarget(LatLon(it.lat, it.lon), it.radiusM, Info) })
            vm.message?.let { Note(it, Info) }
            SummaryCard(t, vehicles.firstOrNull { it.id == t.vehicleId }?.name ?: "?", points.size, vm.rateTenths)
            ClassifyCard(t) { cls, p, b, pe -> vm.classify(cls, p, b, pe) }
            if (t.startsAtHome || t.endsAtHome) HomeReviewCard(t) { d, r -> vm.review(d, r) }
            OdometerCard(t) { s, e, sp, ep -> vm.odometer(s, e, sp, ep) }
            AdjustCard(t) { m, r -> vm.adjust(m, r) }
            GapCard(t, points.map { RawFix(it.time, it.lat, it.lon, it.accuracyM, it.speedMps, it.segment) })
            SectionCard("Bee Maps") {
                Dropdown("Upload status (from Bee Maps app)", UploadStatus.entries, t.beeUploadStatus, { it.name.lowercase() }, { vm.upload(it) })
                if (passed.isEmpty()) Note("No burst targets were passed on this trip.")
                passed.forEach { b -> KV(b.name, when (b.status) { BurstStatus.CREDITED -> "credited"; else -> "passed — not yet credited" }, if (b.status == BurstStatus.CREDITED) Good else Warn) }
                Note("Passing a target is GPS evidence only. Bursts count as credited only after a matching payment is linked.")
            }
            var notes by remember(t.id) { mutableStateOf(t.notes) }
            SectionCard("Notes") {
                TextIn("Notes", notes, { notes = it }, singleLine = false)
                TextButton(onClick = { vm.notes(notes) }, enabled = notes != t.notes) { Text("Save notes") }
            }
            SectionCard("Audit history") {
                if (audit.isEmpty()) Note("No changes recorded.")
                audit.forEach { a ->
                    Text("${Fmt.shortDate(a.time)} ${Fmt.time(a.time)} · ${a.field}: ${a.oldValue ?: "—"} → ${a.newValue ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    if (a.reason.isNotBlank()) Note("   ${a.reason}")
                }
            }
        }
    }
    if (confirmDelete) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete trip?") }, text = {
            Column { Note("The GPS evidence is removed. The deletion and your reason stay in the audit log."); TextIn("Reason (required)", reason, { reason = it }) }
        }, confirmButton = { Button(enabled = reason.isNotBlank(), onClick = { confirmDelete = false; vm.delete(reason, onBack) }, colors = ButtonDefaults.buttonColors(containerColor = Bad)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    }
}

@Composable
private fun SummaryCard(t: Trip, vehicle: String, fixes: Int, rate: Int?) {
    val (miles, basis) = Ledger.totalMiles(t)
    SectionCard("Trip") {
        Row { ClassPill(t); Spacer(Modifier.width(8.dp)); Pill("Confidence ${t.confidence}", if (t.confidence >= 70) Good else if (t.confidence >= 45) Warn else Bad) }
        KV("Time", "${Fmt.time(t.startTime)} – ${Fmt.time(t.endTime)}")
        KV("From", t.startLabel.ifBlank { "—" }); KV("To", t.endLabel.ifBlank { "—" })
        KV("Vehicle", vehicle)
        KV("Total miles", "${Fmt.miles(miles, 2)} (${basis.name.lowercase()})")
        KV("Verified GPS", Fmt.metersAsMiles(t.gpsMeters))
        if (t.gapMeters > 0) KV("Unverified gaps", "${Fmt.metersAsMiles(t.gapMeters)} in ${t.gapCount} gap(s)", Warn)
        if (t.startOdometer != null && t.endOdometer != null) KV("Odometer difference", Fmt.miles(t.endOdometer - t.startOdometer, 1))
        KV("Driving time", Fmt.hm(t.movingMs)); KV("Paused", Fmt.hm(t.pausedMs))
        KV("Elapsed", Fmt.hm(((t.endTime ?: t.startTime) - t.startTime - t.pausedMs).coerceAtLeast(0)))
        KV("Business / personal", "${Fmt.miles(Ledger.businessMiles(t), 2)} / ${Fmt.miles(Ledger.personalMiles(t), 2)}")
        rate?.let { KV("Mileage deduction", if (t.needsTaxReview) "pending review" else Fmt.money(Ledger.businessMiles(t) * it / 1000.0)) }
        KV("GPS fixes stored", "$fixes"); KV("Source", t.source.name.lowercase().replace('_', ' '))
        if (t.startLat != null) KV("Start coords", "%.5f, %.5f".format(t.startLat, t.startLon))
        if (t.endLat != null) KV("End coords", "%.5f, %.5f".format(t.endLat, t.endLon))
    }
}

@Composable
private fun ClassifyCard(t: Trip, onSave: (TripClass, String, Double?, Double?) -> Unit) {
    var cls by remember(t.id, t.classification) { mutableStateOf(t.classification) }
    var purpose by remember(t.id, t.purpose) { mutableStateOf(t.purpose) }
    var biz by remember(t.id, t.businessMiles) { mutableStateOf(if (t.businessMiles > 0) "%.2f".format(t.businessMiles) else "") }
    val total = Ledger.totalMiles(t).first
    SectionCard("Tax classification") {
        if (t.classification == TripClass.UNCLASSIFIED) Note("Not counted for taxes until you confirm a classification.", Warn)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(TripClass.BUSINESS, TripClass.MIXED, TripClass.PERSONAL).forEachIndexed { i, c ->
                SegmentedButton(selected = cls == c, onClick = { cls = c }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(c.name.lowercase().replaceFirstChar { it.uppercase() }) }
            }
        }
        if (cls != TripClass.PERSONAL) TextIn("Business purpose (e.g. Bee Maps mapping – Kankakee Co. route)", purpose, { purpose = it })
        if (cls == TripClass.MIXED) NumberField("Business miles (of %.2f)".format(total), biz, { biz = it }, suffix = "mi")
        val valid = cls != TripClass.UNCLASSIFIED && (cls == TripClass.PERSONAL || purpose.isNotBlank()) &&
            (cls != TripClass.MIXED || (biz.toDoubleOrNull()?.let { it in 0.0..total } == true))
        Button(enabled = valid, onClick = {
            val b = if (cls == TripClass.MIXED) biz.toDoubleOrNull() else null
            onSave(cls, if (cls == TripClass.PERSONAL) "" else purpose, b, b?.let { (total - it).coerceAtLeast(0.0) })
        }) { Text("Confirm classification") }
    }
}

@Composable
private fun HomeReviewCard(t: Trip, onReview: (Boolean, String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    SectionCard("Home travel review") {
        Note("This trip ${if (t.startsAtHome) "starts" else ""}${if (t.startsAtHome && t.endsAtHome) " and " else ""}${if (t.endsAtHome) "ends" else ""} at home. " +
            "Travel between home and work can be non-deductible commuting unless your home is your principal place of business or you travel to a temporary work location. " +
            "MapLedger records these miles but does not count them until you decide.", Warn)
        KV("Status", if (t.needsTaxReview) "Needs review — not counted" else "Reviewed — counted as classified", if (t.needsTaxReview) Warn else Good)
        TextIn("Basis for decision (recorded in audit log)", reason, { reason = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = reason.isNotBlank(), onClick = { onReview(true, reason) }) { Text("Deductible") }
            OutlinedButton(enabled = reason.isNotBlank(), onClick = { onReview(false, reason) }) { Text("Keep excluded") }
        }
    }
}

@Composable
private fun OdometerCard(t: Trip, onSave: (Double?, Double?, String?, String?) -> Unit) {
    var s by remember(t.id, t.startOdometer) { mutableStateOf(t.startOdometer?.toString() ?: "") }
    var e by remember(t.id, t.endOdometer) { mutableStateOf(t.endOdometer?.toString() ?: "") }
    var sp by remember(t.id, t.startOdometerPhoto) { mutableStateOf(t.startOdometerPhoto) }
    var ep by remember(t.id, t.endOdometerPhoto) { mutableStateOf(t.endOdometerPhoto) }
    val sd = s.toDoubleOrNull(); val ed = e.toDoubleOrNull()
    SectionCard("Odometer") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Start", s, { s = it }, Modifier.weight(1f)); NumberField("End", e, { e = it }, Modifier.weight(1f))
        }
        PhotoEvidence("Start photo", sp, "odo_start") { sp = it }
        PhotoEvidence("End photo", ep, "odo_end") { ep = it }
        if (sd != null && ed != null) {
            if (ed < sd) Note("End reading is lower than start.", Bad)
            else {
                val diff = ed - sd; val gps = GeoMath.metersToMiles(t.gpsMeters)
                Note("Odometer %.1f mi vs verified GPS %.2f mi (%+.1f%%)".format(diff, gps, if (diff > 0) (gps - diff) / diff * 100 else 0.0),
                    if (diff > 0 && kotlin.math.abs(gps - diff) / diff > 0.1) Warn else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(enabled = (sd == null || ed == null || ed >= sd), onClick = { onSave(sd, ed, sp, ep) }) { Text("Save odometer") }
    }
}

@Composable
private fun AdjustCard(t: Trip, onSave: (Double?, String) -> Unit) {
    var miles by remember(t.id, t.adjustedMiles) { mutableStateOf(t.adjustedMiles?.toString() ?: "") }
    var reason by remember { mutableStateOf("") }
    SectionCard("Manual mileage adjustment") {
        Note("Overrides GPS/odometer miles for this trip. Every change is kept in the audit history with your reason; adjusted trips get lower confidence.")
        NumberField("Adjusted total miles (blank = none)", miles, { miles = it }, suffix = "mi")
        TextIn("Reason (required)", reason, { reason = it })
        Button(enabled = reason.isNotBlank(), onClick = { onSave(miles.toDoubleOrNull(), reason); reason = "" }) { Text("Apply adjustment") }
    }
}

@Composable
private fun GapCard(t: Trip, fixes: List<RawFix>) {
    if (t.gapCount == 0 && fixes.isEmpty()) return
    val snap = remember(fixes.size) { DistanceEngine.replay(fixes).first.snapshot() }
    SectionCard("GPS evidence quality") {
        KV("Fixes used / rejected", "${snap.acceptedFixes} / ${snap.rejectedFixes}")
        KV("Stationary time", Fmt.hm(snap.stationaryMs))
        if (snap.gaps.isEmpty()) Note("No GPS gaps detected.", Good)
        snap.gaps.forEach { g ->
            Note("Gap ${Fmt.time(g.startTime)}–${Fmt.time(g.endTime)}: %.2f mi straight-line (not counted). ${g.reason}".format(GeoMath.metersToMiles(g.straightLineMeters)), Warn)
        }
        if (snap.gaps.isNotEmpty()) Note("Gap distances are lower bounds (straight line). If you know the real distance (e.g. from odometer), enter it as a manual adjustment with a reason.")
    }
}
