package com.mapledger.app.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapledger.app.AppContainer
import com.mapledger.app.data.db.*
import com.mapledger.app.data.repo.TransferRepository
import com.mapledger.app.domain.GeoMath
import com.mapledger.app.domain.LatLon
import com.mapledger.app.domain.Ledger
import com.mapledger.app.ui.appViewModel
import com.mapledger.app.ui.components.*
import com.mapledger.app.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import kotlin.math.roundToLong

class BeeViewModel(private val c: AppContainer) : ViewModel() {
    val targets = c.bee.observeTargets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val routes = c.bee.observeRoutes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val coverage = c.bee.observeCoverage().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val goals = c.bee.observeGoals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val payments = c.finance.observePayments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trips = c.trips.observeTrips().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var message by mutableStateOf<String?>(null)
    var overlaps by mutableStateOf<List<Triple<PlannedRoute, PlannedRoute, Double>>>(emptyList()); private set
    var selectedRoute by mutableStateOf<Pair<PlannedRoute, List<RoutePoint>>?>(null); private set

    init { viewModelScope.launch { c.bee.reconcile(); overlaps = c.bee.overlappingRoutes() } }

    fun saveTarget(t: BurstTarget) = viewModelScope.launch {
        c.bee.saveTarget(t)
        // Re-check existing trips so a newly added target is matched against history.
        c.db.trips().all().forEach { trip -> if (trip.status == TripStatus.COMPLETED) c.bee.analyzeTrip(trip.id) }
        c.bee.reconcile()
    }
    fun deleteTarget(id: Long) = viewModelScope.launch { c.bee.deleteTarget(id) }
    fun markNotEligible(t: BurstTarget) = viewModelScope.launch { c.bee.saveTarget(t.copy(status = BurstStatus.NOT_ELIGIBLE)) }
    fun linkPayment(t: BurstTarget, p: Payment) = viewModelScope.launch { c.finance.savePayment(p.copy(burstTargetId = t.id, kind = PaymentKind.BURST)); c.bee.reconcile() }
    fun saveGoal(g: HistoricalGoal) = viewModelScope.launch { c.bee.saveGoal(g) }
    fun deleteRoute(id: Long) = viewModelScope.launch { c.bee.deleteRoute(id); overlaps = c.bee.overlappingRoutes(); selectedRoute = null }
    fun openRoute(r: PlannedRoute) = viewModelScope.launch { selectedRoute = r to c.bee.routePoints(r.id) }
    fun closeRoute() { selectedRoute = null }
    fun importGpx(u: Uri, mode: TransferRepository.GpxMode) = viewModelScope.launch {
        message = runCatching { c.transfer.importGpx(u, mode, c.finance.primaryVehicle()?.id).summary }.getOrElse { "Import failed: ${it.message}" }
        overlaps = c.bee.overlappingRoutes(); c.bee.reconcile()
    }
    fun importTargetsCsv(u: Uri) = viewModelScope.launch {
        message = runCatching { c.transfer.importTargetsCsv(u).summary }.getOrElse { "Import failed: ${it.message}" }
        c.db.trips().all().forEach { c.bee.analyzeTrip(it.id) }; c.bee.reconcile()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeeScreen() {
    val vm = appViewModel { BeeViewModel(it) }
    var tab by remember { mutableIntStateOf(0) }
    Column {
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            listOf("Bursts", "Reconcile", "Routes", "Goals").forEachIndexed { i, s -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(s) }) }
        }
        vm.message?.let { Box(Modifier.padding(12.dp)) { Note(it, Info) } }
        when (tab) { 0 -> BurstsTab(vm); 1 -> ReconcileTab(vm); 2 -> RoutesTab(vm); else -> GoalsTab(vm) }
    }
}

private fun statusColor(s: BurstStatus) = when (s) {
    BurstStatus.CREDITED -> Good; BurstStatus.PASSED_UNVERIFIED -> Info; BurstStatus.MISSING_PAYMENT -> Bad; BurstStatus.PLANNED -> Warn; BurstStatus.NOT_ELIGIBLE -> Color0
}
private val Color0 = androidx.compose.ui.graphics.Color(0xFF8A93A3)
private fun statusLabel(s: BurstStatus) = when (s) {
    BurstStatus.CREDITED -> "credited (payment linked)"; BurstStatus.PASSED_UNVERIFIED -> "passed — awaiting credit"
    BurstStatus.MISSING_PAYMENT -> "passed — payment missing"; BurstStatus.PLANNED -> "not passed yet"; BurstStatus.NOT_ELIGIBLE -> "not eligible"
}

@Composable
private fun BurstsTab(vm: BeeViewModel) {
    val targets by vm.targets.collectAsState()
    val payments by vm.payments.collectAsState()
    var edit by remember { mutableStateOf<BurstTarget?>(null) }
    var linking by remember { mutableStateOf<BurstTarget?>(null) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importTargetsCsv) }
    val gpx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.importGpx(u, TransferRepository.GpxMode.BURST_TARGETS) } }
    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { edit = BurstTarget(name = "", lat = 0.0, lon = 0.0) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Target") })
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                if (targets.isNotEmpty()) TrackMap(emptyList(), Modifier.fillMaxWidth().height(220.dp),
                    targets = targets.map { MapTarget(LatLon(it.lat, it.lon), it.radiusM, statusColor(it.status)) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    OutlinedButton(onClick = { csv.launch(arrayOf("text/*", "*/*")) }) { Text("Import CSV") }
                    OutlinedButton(onClick = { gpx.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*")) }) { Text("Import GPX waypoints") }
                }
                Note("A target turns “passed” when verified GPS comes within its radius. It is only “credited” once you link a received Bee Maps payment.")
            }
            if (targets.isEmpty()) item { EmptyState("No burst targets. Add them from Bee Maps (name + coordinates) or import a list.") }
            items(targets, key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth().clickable { edit = t }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(Fmt.money(t.expectedRewardCents), style = MaterialTheme.typography.titleSmall)
                        }
                        Pill(statusLabel(t.status), statusColor(t.status))
                        if (t.campaignId.isNotBlank()) Note("Campaign ${t.campaignId}")
                        t.firstPassedTime?.let { Note("First passed ${Fmt.date(it)} ${Fmt.time(it)} · passes: ${t.passCount}" + if (t.passCount > 1) " (repeated target)" else "") }
                        if (t.status == BurstStatus.PASSED_UNVERIFIED || t.status == BurstStatus.MISSING_PAYMENT) Row {
                            TextButton(onClick = { linking = t }) { Text("Link payment") }
                            TextButton(onClick = { vm.markNotEligible(t) }) { Text("Not eligible") }
                        }
                    }
                }
            }
        }
    }
    edit?.let { t -> TargetDialog(t, onDismiss = { edit = null }, onDelete = { vm.deleteTarget(t.id); edit = null }) { vm.saveTarget(it); edit = null } }
    linking?.let { t ->
        val candidates = payments.filter { it.burstTargetId == null && it.status == PaymentStatus.RECEIVED && (it.kind == PaymentKind.BURST || it.kind == PaymentKind.BONUS || it.campaignId == t.campaignId) }
        AlertDialog(onDismissRequest = { linking = null }, title = { Text("Link payment to ${t.name}") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (candidates.isEmpty()) Note("No unlinked burst/bonus payments. Add the payment on the Money tab first.")
                candidates.forEach { p ->
                    TextButton(onClick = { vm.linkPayment(t, p); linking = null }) { Text("${Fmt.date(p.date)} · ${Fmt.micros(p.amountMicros)} ${p.currency} · ${Fmt.money(p.usdFmvCents)}") }
                }
            }
        }, confirmButton = { TextButton(onClick = { linking = null }) { Text("Close") } })
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun TargetDialog(t: BurstTarget, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (BurstTarget) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(t.name) }
    var lat by remember { mutableStateOf(if (t.id == 0L) "" else t.lat.toString()) }
    var lon by remember { mutableStateOf(if (t.id == 0L) "" else t.lon.toString()) }
    var radius by remember { mutableStateOf(t.radiusM.toInt().toString()) }
    var campaign by remember { mutableStateOf(t.campaignId) }
    var reward by remember { mutableStateOf(t.expectedRewardCents?.let { "%.2f".format(it / 100.0) } ?: "") }
    var notes by remember { mutableStateOf(t.notes) }
    val la = lat.toDoubleOrNull(); val lo = lon.toDoubleOrNull(); val r = radius.toDoubleOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (t.id == 0L) "Add burst target" else "Edit target") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextIn("Name", name, { name = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NumberField("Latitude", lat, { lat = it }, Modifier.weight(1f)); NumberField("Longitude", lon, { lon = it }, Modifier.weight(1f)) }
            if (hasPreciseLocation(ctx)) TextButton(onClick = {
                scope.launch {
                    runCatching { LocationServices.getFusedLocationProviderClient(ctx).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await() }
                        .getOrNull()?.let { lat = "%.6f".format(it.latitude); lon = "%.6f".format(it.longitude) }
                }
            }) { Text("Use my current location") }
            NumberField("Radius", radius, { radius = it }, suffix = "m")
            TextIn("Campaign ID", campaign, { campaign = it })
            NumberField("Expected reward", reward, { reward = it }, suffix = "USD")
            TextIn("Notes", notes, { notes = it }, singleLine = false)
        }
    }, confirmButton = {
        Button(enabled = name.isNotBlank() && la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0 && r != null && r > 0, onClick = {
            onSave(t.copy(name = name.trim(), lat = la!!, lon = lo!!, radiusM = r!!, campaignId = campaign.trim(),
                expectedRewardCents = reward.toDoubleOrNull()?.let { (it * 100).roundToLong() }, notes = notes))
        }) { Text("Save") }
    }, dismissButton = { Row { if (t.id != 0L) TextButton(onClick = onDelete) { Text("Delete", color = Bad) }; TextButton(onClick = onDismiss) { Text("Cancel") } } })
}

@Composable
private fun ReconcileTab(vm: BeeViewModel) {
    val targets by vm.targets.collectAsState()
    val payments by vm.payments.collectAsState()
    val received = payments.filter { it.status == PaymentStatus.RECEIVED }
    val passed = targets.filter { it.status in setOf(BurstStatus.PASSED_UNVERIFIED, BurstStatus.MISSING_PAYMENT, BurstStatus.CREDITED) }
    val expected = passed.sumOf { it.expectedRewardCents ?: 0 }
    val linked = received.filter { it.burstTargetId != null }.sumOf { it.usdFmvCents ?: 0 }
    val burstAll = received.filter { it.kind == PaymentKind.BURST }.sumOf { it.usdFmvCents ?: 0 }
    val base = received.filter { it.kind == PaymentKind.BASE }.sumOf { it.usdFmvCents ?: 0 }
    val overdue = payments.filter { it.status == PaymentStatus.EXPECTED && System.currentTimeMillis() - it.date > 7 * 86_400_000L }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("Burst rewards: expected vs actual") {
            KV("Expected (passed targets)", Fmt.money(expected))
            KV("Received & linked to targets", Fmt.money(linked), Good)
            KV("Difference", Fmt.money(linked - expected), if (linked >= expected) Good else Warn)
            KV("All burst payments", Fmt.money(burstAll))
            KV("Regular mapping (base) rewards", Fmt.money(base))
        }
        SectionCard("Possibly missing burst payments") {
            val missing = targets.filter { it.status == BurstStatus.MISSING_PAYMENT }
            if (missing.isEmpty()) Note("None. (Threshold is set in Settings.)", Good)
            missing.forEach { KV(it.name, "passed ${it.firstPassedTime?.let(Fmt::shortDate) ?: "?"} · ${Fmt.money(it.expectedRewardCents)}", Bad) }
            Note("“Missing” means we have GPS evidence of passing and no linked payment yet. Check the Bee Maps app — the burst may not have qualified.")
        }
        SectionCard("Delayed expected payments") {
            if (overdue.isEmpty()) Note("No expected payments older than 7 days.", Good)
            overdue.forEach { KV("${Fmt.date(it.date)} ${it.kind.name.lowercase()}", Fmt.money(it.expectedUsdCents), Warn) }
        }
        SectionCard("Repeated targets") {
            val rep = targets.filter { it.passCount > 1 }
            if (rep.isEmpty()) Note("No targets passed more than once.")
            rep.forEach { KV(it.name, "${it.passCount} passes") }
            Note("Repeated passes usually earn once. Avoid planning routes that re-collect the same target.")
        }
    }
}

@Composable
private fun RoutesTab(vm: BeeViewModel) {
    val routes by vm.routes.collectAsState()
    val coverage by vm.coverage.collectAsState()
    val gpx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.importGpx(u, TransferRepository.GpxMode.PLANNED_ROUTE) } }
    val sel = vm.selectedRoute
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedButton(onClick = { gpx.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*")) }) { Text("Import planned route (GPX)") }
            Note("Coverage = share of the planned route with verified GPS within 30 m from any trip.")
        }
        if (sel != null) item {
            val (r, pts) = sel
            SectionCard(r.name, action = { TextButton(onClick = vm::closeRoute) { Text("Close") } }) {
                val covered = pts.filter { it.coveredByTripId != null }
                // Draw covered stretches as the "track" and the full route dashed underneath.
                val segs = mutableListOf<MutableList<LatLon>>()
                pts.forEach { p -> if (p.coveredByTripId != null) { if (segs.isEmpty() || pts.getOrNull(p.seq - 1)?.coveredByTripId == null) segs += mutableListOf<LatLon>(); segs.last() += LatLon(p.lat, p.lon) } }
                TrackMap(segs, Modifier.fillMaxWidth().height(260.dp), planned = listOf(pts.map { LatLon(it.lat, it.lon) }))
                KV("Covered", "${covered.size}/${pts.size} points (${Fmt.pct(if (pts.isEmpty()) 0.0 else covered.size.toDouble() / pts.size)})")
                TextButton(onClick = { vm.deleteRoute(r.id) }) { Text("Delete route", color = Bad) }
            }
        }
        if (routes.isEmpty()) item { EmptyState("No planned routes imported.") }
        items(routes, key = { it.id }) { r ->
            val cov = coverage.firstOrNull { it.routeId == r.id }
            val f = if (cov == null || cov.total == 0) 0.0 else cov.covered.toDouble() / cov.total
            Card(Modifier.fillMaxWidth().clickable { vm.openRoute(r) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.name, style = MaterialTheme.typography.titleSmall)
                    Note("${Fmt.metersAsMiles(r.lengthMeters, 1)} · imported ${Fmt.shortDate(r.importedAt)} · ${r.sourceFile}")
                    LinearProgressIndicator(progress = { f.toFloat() }, Modifier.fillMaxWidth())
                    Note("${Fmt.pct(f)} completed")
                }
            }
        }
        if (vm.overlaps.isNotEmpty()) item {
            SectionCard("Overlapping routes") {
                vm.overlaps.forEach { (a, b, f) -> Note("${b.name} overlaps ${a.name} by ${Fmt.pct(f)}", Warn) }
            }
        }
    }
}

@Composable
private fun GoalsTab(vm: BeeViewModel) {
    val goals by vm.goals.collectAsState()
    val trips by vm.trips.collectAsState()
    var edit by remember { mutableStateOf<HistoricalGoal?>(null) }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionCard("Bee Maps road goals") {
                Note("These are road-goal distances recorded by Bee Maps — not odometer readings and not necessarily deductible miles. " +
                    "They are kept separate from verified GPS mileage and never enter the tax log.")
                KV("Total recorded", "%.1f km (%.1f mi)".format(goals.sumOf { it.distanceKm }, GeoMath.kmToMiles(goals.sumOf { it.distanceKm })))
                TextButton(onClick = { edit = HistoricalGoal(weekStart = Ledger.periodStart(LocalDate.now(), Ledger.Period.WEEK).toEpochDay(), distanceKm = 0.0) }) { Text("Add week") }
            }
        }
        items(goals.sortedByDescending { it.weekStart }, key = { it.id }) { g ->
            val start = LocalDate.ofEpochDay(g.weekStart)
            val gpsMi = trips.filter { it.status == TripStatus.COMPLETED && it.source == RecordSource.LIVE_GPS }
                .filter { val d = Fmt.localDate(it.startTime); !d.isBefore(start) && d.isBefore(start.plusDays(7)) }
                .sumOf { GeoMath.metersToMiles(it.gpsMeters) }
            Card(Modifier.fillMaxWidth().clickable { edit = g }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(14.dp)) {
                    Row { Text("Week of ${Fmt.iso(start)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text("%.1f km".format(g.distanceKm), style = MaterialTheme.typography.titleSmall) }
                    Note("= %.1f mi road goal · verified GPS driven that week: %.1f mi".format(GeoMath.kmToMiles(g.distanceKm), gpsMi))
                    if (g.evidencePhoto != null) Note("Screenshot attached", Good)
                }
            }
        }
    }
    edit?.let { g ->
        var date by remember(g) { mutableStateOf(LocalDate.ofEpochDay(g.weekStart)) }
        var km by remember(g) { mutableStateOf(if (g.distanceKm > 0) g.distanceKm.toString() else "") }
        var photo by remember(g) { mutableStateOf(g.evidencePhoto) }
        var notes by remember(g) { mutableStateOf(g.notes) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text("Road goal") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Week starting", date, { date = it })
                NumberField("Distance", km, { km = it }, suffix = "km")
                PhotoEvidence("Bee Maps screenshot", photo, "beemaps") { photo = it }
                TextIn("Notes", notes, { notes = it })
            }
        }, confirmButton = {
            Button(enabled = km.toDoubleOrNull() != null, onClick = {
                vm.saveGoal(g.copy(weekStart = date.toEpochDay(), distanceKm = km.toDouble(), evidencePhoto = photo, notes = notes,
                    source = if (g.id == 0L) "Bee Maps road goal (manual entry)" else g.source)); edit = null
            }) { Text("Save") }
        }, dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } })
    }
}
