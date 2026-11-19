package com.mapledger.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapledger.app.AppContainer
import com.mapledger.app.data.db.PaymentStatus
import com.mapledger.app.data.db.Trip
import com.mapledger.app.data.db.Vehicle
import com.mapledger.app.domain.GeoMath
import com.mapledger.app.domain.TaxMath
import com.mapledger.app.tracking.GpsQuality
import com.mapledger.app.tracking.TrackingService
import com.mapledger.app.tracking.TrackingState
import com.mapledger.app.ui.appViewModel
import com.mapledger.app.ui.components.*
import com.mapledger.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class DriveViewModel(private val c: AppContainer) : ViewModel() {
    val live = TrackingState.state
    val activeTrip = c.trips.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val vehicles = c.finance.observeVehicles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val todayEarningsCents = c.finance.observePayments().combine(c.trips.observeTrips()) { p, _ ->
        val today = LocalDate.now()
        p.filter { it.status == PaymentStatus.RECEIVED && Fmt.localDate(it.date) == today }.sumOf { it.usdFmvCents ?: 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    var rateTenths by mutableStateOf<Int?>(null); private set
    var lastFinished by mutableStateOf<Trip?>(null); private set

    init {
        viewModelScope.launch { rateTenths = TaxMath.rateFor(LocalDate.now(), c.finance.rates())?.tenthsOfCent }
        viewModelScope.launch { c.trips.observeTrips().collect { list -> lastFinished = list.firstOrNull { it.status == com.mapledger.app.data.db.TripStatus.COMPLETED } } }
    }

    fun setVehicle(id: Long) = viewModelScope.launch { c.settings.setActiveVehicle(id) }

    /** Finish an interrupted trip without restarting GPS. */
    fun finishInterrupted(trip: Trip, odo: Double?, photo: String?) = c.appScope.launch {
        c.trips.finalizeTrip(trip.id, odo, photo) { lat, lon -> c.geocoder.label(lat, lon) }
        c.bee.analyzeTrip(trip.id); c.bee.reconcile()
    }
}

fun hasPreciseLocation(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
fun hasNotificationPermission(ctx: Context) = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
fun isBatteryUnrestricted(ctx: Context) = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
fun openBatterySettings(ctx: Context) {
    runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        .onFailure { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
}
fun openAppSettings(ctx: Context) = ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))

/** Re-evaluates [check] every time the screen resumes (e.g. returning from system settings). */
@Composable
fun rememberOnResume(check: () -> Boolean): Boolean {
    var v by remember { mutableStateOf(check()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) v = check() }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return v
}

@Composable
fun DriveScreen(confirmStart: Boolean = false) {
    val vm = appViewModel { DriveViewModel(it) }
    val ctx = LocalContext.current
    val live by vm.live.collectAsState()
    val active by vm.activeTrip.collectAsState()
    val vehicles by vm.vehicles.collectAsState()
    val settings by vm.settings.collectAsState()
    val today by vm.todayEarningsCents.collectAsState()

    var permTick by remember { mutableIntStateOf(0) }
    val precise = remember(permTick) { hasPreciseLocation(ctx) }
    val notif = remember(permTick) { hasNotificationPermission(ctx) }
    val battery = rememberOnResume { isBatteryUnrestricted(ctx) }
    var showStart by remember { mutableStateOf(confirmStart) }
    var showStop by remember { mutableStateOf(false) }
    var showFinishInterrupted by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    fun requestPerms() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        permLauncher.launch(perms.toTypedArray())
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live.running) { while (live.running) { now = System.currentTimeMillis(); delay(1000) } }

    val interrupted = active != null && !live.running
    val activeVehicle = vehicles.firstOrNull { it.id == settings?.activeVehicleId } ?: vehicles.firstOrNull()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        val map: @Composable (Modifier) -> Unit = { m ->
            val segs = if (live.running) live.path else emptyList()
            TrackMap(segs, m, follow = live.running)
        }
        val panel: @Composable (Modifier) -> Unit = { m ->
            Column(m.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!precise) WarningCard(Icons.Default.LocationOff, "Precise location needed",
                    "MapLedger records mileage only while a trip is running, using a visible notification. Approximate location is not accurate enough for a mileage log.",
                    "Grant permission") { requestPerms() }
                else if (!notif) WarningCard(Icons.Default.Warning, "Notifications are off",
                    "Android requires the tracking notification so recording can continue with the screen locked. Allow it so Pause/Stop controls appear.",
                    "Allow") { requestPerms() }
                if (!battery) WarningCard(Icons.Default.BatteryAlert, "Battery optimisation may stop tracking",
                    "On Samsung Galaxy: Settings › Battery › Background usage limits › add MapLedger to “Never sleeping apps”, and set App battery usage to Unrestricted.",
                    "Open battery settings") { openBatterySettings(ctx) }
                if (interrupted) active?.let { t ->
                    SectionCard("Recording interrupted") {
                        Note("Trip started ${Fmt.time(t.startTime)} is saved with ${Fmt.metersAsMiles(t.gpsMeters)} of verified GPS. Miles driven while it was not recording are not invented; if you resume, the gap is shown as unverified.", Warn)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { if (precise) TrackingService.send(ctx, TrackingService.ACTION_RECOVER) else requestPerms() }) { Text("Resume recording") }
                            OutlinedButton(onClick = { showFinishInterrupted = true }) { Text("Finish trip") }
                        }
                    }
                }
                live.error?.let { Note(it, Bad) }

                // Big live numbers
                SectionCard {
                    Text(if (live.running) (if (live.paused) "PAUSED" else "RECORDING") else "READY",
                        style = MaterialTheme.typography.labelSmall, color = if (live.running && !live.paused) Good else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%.2f".format(GeoMath.metersToMiles(live.verifiedMeters)), fontSize = 64.sp, fontWeight = FontWeight.Bold, lineHeight = 64.sp)
                    Text("verified miles", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatRow(
                        { Stat("Duration", Fmt.duration(live.elapsedMs(now)), Modifier.weight(1f)) },
                        { Stat("Speed", "${Fmt.mph(live.speedMps)} mph", Modifier.weight(1f)) },
                    )
                    val q = GpsQuality.of(live.accuracyM, now - (live.lastFixTime ?: 0))
                    StatRow(
                        { Stat("GPS", q.label, Modifier.weight(1f), sub = live.accuracyM?.let { "±%.0f m".format(it) },
                            color = when (q) { GpsQuality.EXCELLENT, GpsQuality.GOOD -> Good; GpsQuality.FAIR -> Warn; else -> Bad }) },
                        { Stat("Unverified gaps", Fmt.metersAsMiles(live.gapMeters), Modifier.weight(1f), color = if (live.gapMeters > 0) Warn else MaterialTheme.colorScheme.onSurface) },
                    )
                    StatRow(
                        { Stat("Today's payouts", Fmt.money(today), Modifier.weight(1f)) },
                        { Stat("Mileage value", vm.rateTenths?.let { Fmt.money(GeoMath.metersToMiles(live.verifiedMeters) * it / 1000.0) } ?: "—", Modifier.weight(1f),
                            sub = "if business (IRS rate)") },
                    )
                    if (live.running) Note("${live.acceptedFixes} fixes used · ${live.rejectedFixes} rejected (noise/duplicates/spikes)")
                }

                // Controls — large, thumb-reachable
                if (!live.running) {
                    Button(onClick = { if (!precise) requestPerms() else showStart = true }, enabled = !interrupted,
                        modifier = Modifier.fillMaxWidth().height(76.dp), colors = ButtonDefaults.buttonColors(containerColor = Good)) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(32.dp)); Spacer(Modifier.width(8.dp))
                        Text("START MAPPING SESSION", style = MaterialTheme.typography.titleMedium)
                    }
                    activeVehicle?.let { Note("Vehicle: ${it.name}. Start at home to capture the full home → mapping → home journey.") }
                    vm.lastFinished?.let { t -> Note("Last trip: ${Fmt.date(t.startTime)} · ${Fmt.miles(com.mapledger.app.domain.Ledger.totalMiles(t).first, 2)} · saved to Trips") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (live.paused) Button(onClick = { TrackingService.send(ctx, TrackingService.ACTION_RESUME) }, Modifier.weight(1f).height(76.dp)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("RESUME")
                        } else FilledTonalButton(onClick = { TrackingService.send(ctx, TrackingService.ACTION_PAUSE) }, Modifier.weight(1f).height(76.dp)) {
                            Icon(Icons.Default.Pause, null); Spacer(Modifier.width(6.dp)); Text("PAUSE")
                        }
                        Button(onClick = { showStop = true }, Modifier.weight(1f).height(76.dp), colors = ButtonDefaults.buttonColors(containerColor = Bad)) {
                            Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("STOP")
                        }
                    }
                    Note("Recording continues with the screen locked and while Bee Maps or Google Maps is open. Use the notification to pause or stop.")
                }
            }
        }
        if (wide) Row(Modifier.fillMaxSize()) { map(Modifier.weight(1.2f).fillMaxHeight()); panel(Modifier.weight(1f).fillMaxHeight()) }
        else Column(Modifier.fillMaxSize()) { map(Modifier.fillMaxWidth().height(260.dp)); panel(Modifier.weight(1f)) }
    }

    if (showStart) StartDialog(vehicles, activeVehicle, onDismiss = { showStart = false }) { v, odo, photo ->
        showStart = false
        vm.setVehicle(v.id)
        if (!notif && Build.VERSION.SDK_INT >= 33) requestPerms()
        TrackingService.send(ctx, TrackingService.ACTION_START) {
            putExtra(TrackingService.EXTRA_VEHICLE, v.id); odo?.let { putExtra(TrackingService.EXTRA_ODO, it) }; photo?.let { putExtra(TrackingService.EXTRA_PHOTO, it) }
        }
    }
    if (showStop) OdometerDialog("Stop trip", "Ending odometer (optional — add later in Trips)", onDismiss = { showStop = false }) { odo, photo ->
        showStop = false
        TrackingService.send(ctx, TrackingService.ACTION_STOP) { odo?.let { putExtra(TrackingService.EXTRA_ODO, it) }; photo?.let { putExtra(TrackingService.EXTRA_PHOTO, it) } }
    }
    if (showFinishInterrupted) active?.let { t ->
        OdometerDialog("Finish interrupted trip", "Ending odometer (optional)", onDismiss = { showFinishInterrupted = false }) { odo, photo ->
            showFinishInterrupted = false; vm.finishInterrupted(t, odo, photo)
        }
    }
}

@Composable
private fun WarningCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, action: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Honey); Spacer(Modifier.width(8.dp)); Text(title, style = MaterialTheme.typography.titleMedium) }
            Text(text, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun StartDialog(vehicles: List<Vehicle>, initial: Vehicle?, onDismiss: () -> Unit, onStart: (Vehicle, Double?, String?) -> Unit) {
    var vehicle by remember { mutableStateOf(initial ?: vehicles.firstOrNull()) }
    var odo by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Start mapping session") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vehicles.isEmpty()) Note("Add a vehicle in Settings first.", Bad)
            vehicle?.let { v -> Dropdown("Vehicle", vehicles, v, { it.name }, { vehicle = it }) }
            NumberField("Starting odometer", odo, { odo = it }, suffix = "mi")
            PhotoEvidence("Odometer photo", photo, "odo_start") { photo = it }
            Note("GPS recording starts now. The trip stays unclassified until you confirm it as business or personal.")
        }
    }, confirmButton = {
        Button(onClick = { vehicle?.let { onStart(it, odo.toDoubleOrNull(), photo) } }, enabled = vehicle != null) { Icon(Icons.Default.GpsFixed, null); Spacer(Modifier.width(6.dp)); Text("Start") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun OdometerDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (Double?, String?) -> Unit) {
    var odo by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(label, odo, { odo = it }, suffix = "mi")
            PhotoEvidence("Odometer photo", photo, "odo_end") { photo = it }
        }
    }, confirmButton = { Button(onClick = { onConfirm(odo.toDoubleOrNull(), photo) }) { Text("Save trip") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
