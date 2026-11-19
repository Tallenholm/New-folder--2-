package com.mapledger.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapledger.app.AppContainer
import com.mapledger.app.BuildConfig
import com.mapledger.app.data.db.AppDatabase
import com.mapledger.app.data.db.DeductionMethod
import com.mapledger.app.data.db.Vehicle
import com.mapledger.app.data.repo.TransferRepository
import com.mapledger.app.tracking.DriveDetectionReceiver
import com.mapledger.app.ui.appViewModel
import com.mapledger.app.ui.components.*
import com.mapledger.app.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val vehicles = c.finance.observeVehicles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rates = c.finance.observeRates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val imports = c.db.imports().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var message by mutableStateOf<String?>(null)

    fun saveVehicle(v: Vehicle) = viewModelScope.launch { val id = c.finance.saveVehicle(v); if (v.isPrimary) c.settings.setActiveVehicle(id) }
    fun setHome(lat: Double, lon: Double, label: String, radius: Double, approx: Boolean) = viewModelScope.launch { c.settings.setHome(lat, lon, label, radius, approx) }
    fun setBiometric(b: Boolean) = viewModelScope.launch { c.settings.setBiometric(b) }
    fun setDetection(b: Boolean) = viewModelScope.launch { c.settings.setDriveDetection(b) }
    fun setTaxRate(r: Double) = viewModelScope.launch { c.settings.setMarginalTaxRate(r) }
    fun setMissingDays(d: Long) = viewModelScope.launch { c.settings.setMissingPaymentDays(d); c.bee.reconcile() }
    fun setAccuracy(m: Double) = viewModelScope.launch { c.settings.setMaxAccuracy(m) }
    fun addRate(from: LocalDate, to: LocalDate, tenths: Int, label: String) = viewModelScope.launch { c.finance.addRate(from, to, tenths, label) }
    fun deleteRate(id: Long) = viewModelScope.launch { c.finance.deleteRate(id) }

    private fun op(block: suspend () -> String) = viewModelScope.launch { message = "Working…"; message = runCatching { block() }.getOrElse { "Failed: ${it.message}" } }
    private suspend fun vid() = settings.value?.activeVehicleId ?: c.finance.primaryVehicle()?.id ?: error("Add a vehicle first")
    fun importTimeline(u: Uri) = op { c.transfer.importTimeline(u, vid()).summary }
    fun importGpxTrip(u: Uri) = op { c.transfer.importGpx(u, TransferRepository.GpxMode.DRIVEN_TRIP, vid()).summary }
    fun importLedger(u: Uri) = op { c.transfer.importLedger(u, vid()).summary }
    fun importPayments(u: Uri) = op { c.transfer.importPayments(u).summary }
    fun backup(u: Uri, pass: String) = op { c.transfer.backup(u, pass.toCharArray()); "Encrypted backup saved. Keep the passphrase safe — it cannot be recovered." }
    fun restore(u: Uri, pass: String) = op {
        val n = runCatching { c.transfer.restore(u, pass.toCharArray()) }.getOrElse { if (it is javax.crypto.AEADBadTagException) error("Wrong passphrase or damaged file") else throw it }
        "Restored $n trips."
    }
}

@SuppressLint("MissingPermission")
@Composable
fun SettingsScreen() {
    val vm = appViewModel { SettingsViewModel(it) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by vm.settings.collectAsState()
    val vehicles by vm.vehicles.collectAsState()
    val rates by vm.rates.collectAsState()
    val imports by vm.imports.collectAsState()
    var editVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var showRate by remember { mutableStateOf(false) }
    var passFor by remember { mutableStateOf<Pair<String, Uri>?>(null) }
    var permTick by remember { mutableIntStateOf(0) }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val arPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permTick++; if (ok) { DriveDetectionReceiver.enable(ctx); vm.setDetection(true) }
    }
    val timeline = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importTimeline) }
    val gpxTrip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importGpxTrip) }
    val ledger = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importLedger) }
    val payments = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importPayments) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let { u -> passFor = "backup" to u } }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> passFor = "restore" to u } }

    val st = s ?: return
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        vm.message?.let { SectionCard { Note(it, Info) } }

        SectionCard("Vehicles", action = { TextButton(onClick = { editVehicle = Vehicle(name = "", year = null, make = "", model = "") }) { Text("Add") } }) {
            vehicles.forEach { v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${v.name}${if (v.isPrimary) " (primary)" else ""}", style = MaterialTheme.typography.titleSmall)
                        Note("${v.year ?: ""} ${v.make} ${v.model} ${v.trim} · ${v.deductionMethod.name.lowercase().replace('_', ' ')}")
                    }
                    TextButton(onClick = { editVehicle = v }) { Text("Edit") }
                }
            }
        }

        SectionCard("Home base") {
            KV("Label", st.homeLabel)
            KV("Location", if (st.homeLat != null) "%.5f, %.5f".format(st.homeLat, st.homeLon) else "not set")
            KV("Detection radius", "%.0f m".format(st.homeRadiusM))
            if (st.homeApproximate) Note("Approximate: Manteno, IL village centre with a wide radius. Stand at home and tap below for accurate home-travel flags.", Warn)
            Button(enabled = hasPreciseLocation(ctx), onClick = {
                scope.launch {
                    val loc = runCatching { LocationServices.getFusedLocationProviderClient(ctx).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await() }.getOrNull()
                    if (loc != null) vm.setHome(loc.latitude, loc.longitude, "Home (Manteno, IL)", 150.0, false) else vm.message = "Could not get a GPS fix"
                }
            }) { Text("Set home to my current location") }
        }

        val precise = remember(permTick) { hasPreciseLocation(ctx) }
        val notif = remember(permTick) { hasNotificationPermission(ctx) }
        val battery = rememberOnResume { isBatteryUnrestricted(ctx) }
        SectionCard("Permissions & reliability") {
            KV("Precise location (while using)", if (precise) "granted" else "missing", if (precise) Good else Bad)
            KV("Notifications", if (notif) "granted" else "missing", if (notif) Good else Warn)
            KV("Background location", "not requested — not needed", Good)
            KV("Battery optimisation", if (battery) "unrestricted" else "may stop tracking", if (battery) Good else Warn)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    perm.launch(buildList { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }.toTypedArray())
                }) { Text("Request") }
                OutlinedButton(onClick = { openBatterySettings(ctx) }) { Text("Battery") }
                OutlinedButton(onClick = { openAppSettings(ctx) }) { Text("App info") }
            }
            Note("MapLedger tracks only during a session you start, with a visible notification. That is why “Allow all the time” is not needed.")
        }

        SectionCard("Drive detection (optional)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Prompt me when driving starts", Modifier.weight(1f))
                Switch(checked = st.driveDetection, onCheckedChange = { on ->
                    if (on) { if (DriveDetectionReceiver.hasPermission(ctx)) { DriveDetectionReceiver.enable(ctx); vm.setDetection(true) } else arPerm.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
                    else { DriveDetectionReceiver.disable(ctx); vm.setDetection(false) }
                })
            }
            Note("Uses Android activity recognition to send a notification. You still confirm every trip start, and detected trips are never auto-classified for taxes.")
        }

        SectionCard("Privacy & security") {
            val canBio = remember { BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Require fingerprint / face / PIN to open", Modifier.weight(1f))
                Switch(checked = st.biometricLock, enabled = canBio, onCheckedChange = vm::setBiometric)
            }
            if (!canBio) Note("No biometric or screen lock set up on this device.", Warn)
            Note("All data stays on this phone. Location history is excluded from Google cloud backup and device transfer; use the encrypted backup below. No analytics, no accounts, no network sync.")
        }

        SectionCard("Encrypted backup") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { backup.launch("MapLedger-${LocalDate.now()}.mlbackup") }) { Text("Back up") }
                OutlinedButton(onClick = { restore.launch(arrayOf("*/*")) }) { Text("Restore") }
            }
            Note("AES-256-GCM with a passphrase you choose. Save the file to Google Drive, a USB stick, or Samsung My Files. Restore replaces all data on this phone.")
        }

        SectionCard("Import history") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { timeline.launch(arrayOf("application/json", "*/*")) }, Modifier.weight(1f)) { Text("Google Timeline") }
                OutlinedButton(onClick = { gpxTrip.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*")) }, Modifier.weight(1f)) { Text("GPX trips") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { ledger.launch(arrayOf("text/*", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*")) }, Modifier.weight(1f)) { Text("Excel/CSV ledger") }
                OutlinedButton(onClick = { payments.launch(arrayOf("text/*", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*")) }, Modifier.weight(1f)) { Text("Payments") }
            }
            Note("Imports go to the active vehicle, arrive unclassified, and keep their source and confidence. The same file or transaction is never imported twice. Bee Maps screenshots can be attached as evidence on the Bee Maps › Goals tab.")
            imports.take(6).forEach { Note("${Fmt.shortDate(it.importedAt)} · ${it.type} · ${it.fileName}: +${it.rowsImported}, skipped ${it.rowsSkipped}") }
        }

        SectionCard("IRS mileage rates", action = { TextButton(onClick = { showRate = true }) { Text("Add") } }) {
            rates.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("%.1f¢/mi".format(r.tenthsOfCent / 10.0), style = MaterialTheme.typography.titleSmall)
                        Note("${LocalDate.ofEpochDay(r.fromEpochDay)} → ${LocalDate.ofEpochDay(r.toEpochDay)} · ${r.label}")
                    }
                    TextButton(onClick = { vm.deleteRate(r.id) }) { Text("Remove", color = Bad) }
                }
            }
        }

        SectionCard("Calculation settings") {
            var tax by remember(st.marginalTaxRate) { mutableStateOf("%.0f".format(st.marginalTaxRate * 100)) }
            var days by remember(st.missingPaymentDays) { mutableStateOf(st.missingPaymentDays.toString()) }
            var acc by remember(st.maxAccuracyM) { mutableStateOf("%.0f".format(st.maxAccuracyM)) }
            NumberField("Marginal income-tax rate for estimates", tax, { tax = it }, suffix = "%")
            NumberField("Flag burst payment missing after", days, { days = it }, decimal = false, suffix = "days")
            NumberField("Reject GPS fixes worse than", acc, { acc = it }, suffix = "m")
            Button(onClick = {
                tax.toDoubleOrNull()?.let { vm.setTaxRate((it / 100).coerceIn(0.0, 0.5)) }
                days.toLongOrNull()?.let { vm.setMissingDays(it.coerceIn(1, 365)) }
                acc.toDoubleOrNull()?.let { vm.setAccuracy(it.coerceIn(10.0, 100.0)) }
            }) { Text("Save") }
        }

        SectionCard("About") {
            KV("Version", BuildConfig.VERSION_NAME); KV("Database schema", "v${AppDatabase.VERSION}")
            KV("Map", if (BuildConfig.MAPS_API_KEY.isNotBlank()) "Google Maps" else "Offline trace (no Maps API key)")
            Note("MapLedger is a record-keeping tool, not tax advice.")
        }
    }

    editVehicle?.let { v -> VehicleDialog(v, onDismiss = { editVehicle = null }) { vm.saveVehicle(it); editVehicle = null } }
    if (showRate) RateDialog(onDismiss = { showRate = false }) { f, t, tenths, l -> vm.addRate(f, t, tenths, l); showRate = false }
    passFor?.let { (mode, uri) ->
        var pass by remember { mutableStateOf("") }; var pass2 by remember { mutableStateOf("") }
        val ok = pass.length >= 8 && (mode == "restore" || pass == pass2)
        AlertDialog(onDismissRequest = { passFor = null }, title = { Text(if (mode == "backup") "Backup passphrase" else "Restore backup") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pass, { pass = it }, label = { Text("Passphrase (8+ characters)") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                if (mode == "backup") OutlinedTextField(pass2, { pass2 = it }, label = { Text("Repeat passphrase") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                if (mode == "restore") Note("This replaces ALL trips, expenses and payments on this phone.", Warn)
            }
        }, confirmButton = {
            Button(enabled = ok, onClick = { if (mode == "backup") vm.backup(uri, pass) else vm.restore(uri, pass); passFor = null }) { Text(if (mode == "backup") "Encrypt & save" else "Restore") }
        }, dismissButton = { TextButton(onClick = { passFor = null }) { Text("Cancel") } })
    }
}

@Composable
private fun VehicleDialog(v: Vehicle, onDismiss: () -> Unit, onSave: (Vehicle) -> Unit) {
    var name by remember { mutableStateOf(v.name) }
    var year by remember { mutableStateOf(v.year?.toString() ?: "") }
    var make by remember { mutableStateOf(v.make) }; var model by remember { mutableStateOf(v.model) }; var trim by remember { mutableStateOf(v.trim) }
    var method by remember { mutableStateOf(v.deductionMethod) }
    var firstYear by remember { mutableStateOf(v.firstBusinessYear?.toString() ?: "") }
    var primary by remember { mutableStateOf(v.isPrimary) }
    var rental by remember { mutableStateOf(v.isRental) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (v.id == 0L) "Add vehicle" else "Edit vehicle") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextIn("Nickname", name, { name = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NumberField("Year", year, { year = it }, Modifier.weight(1f), decimal = false); TextIn("Make", make, { make = it }, Modifier.weight(1f)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextIn("Model", model, { model = it }, Modifier.weight(1f)); TextIn("Trim", trim, { trim = it }, Modifier.weight(1f)) }
            Dropdown("Deduction method", DeductionMethod.entries, method, { it.name.lowercase().replace('_', ' ') }, { method = it })
            Note(when (method) {
                DeductionMethod.STANDARD_MILEAGE -> "Rate × business miles. Gas, repairs, tires, insurance and lease costs for this vehicle are NOT deducted separately (prevents double-counting). Parking and tolls still count."
                DeductionMethod.ACTUAL_EXPENSES -> "Business-use % × actual operating costs. The mileage rate is not applied. If you used actual expenses in this vehicle's first business year you generally can't switch to standard mileage later."
                DeductionMethod.UNDECIDED -> "Estimates use standard mileage and show actual expenses for comparison."
            }, if (method == DeductionMethod.UNDECIDED) Warn else MaterialTheme.colorScheme.onSurfaceVariant)
            NumberField("First year used for business", firstYear, { firstYear = it }, decimal = false)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(primary, { primary = it }); Text("Primary vehicle") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(rental, { rental = it }); Text("Rental / leased") }
        }
    }, confirmButton = {
        Button(enabled = name.isNotBlank(), onClick = {
            onSave(v.copy(name = name.trim(), year = year.toIntOrNull(), make = make.trim(), model = model.trim(), trim = trim.trim(), deductionMethod = method,
                firstBusinessYear = firstYear.toIntOrNull(), isPrimary = primary, isRental = rental))
        }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun RateDialog(onDismiss: () -> Unit, onSave: (LocalDate, LocalDate, Int, String) -> Unit) {
    var from by remember { mutableStateOf(LocalDate.now().withDayOfYear(1)) }
    var to by remember { mutableStateOf(LocalDate.now().withMonth(12).withDayOfMonth(31)) }
    var cents by remember { mutableStateOf("") }; var label by remember { mutableStateOf("IRS business rate") }
    val c = cents.toDoubleOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add mileage rate") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("From", from, { from = it }); DateField("To", to, { to = it })
            NumberField("Cents per mile", cents, { cents = it }, suffix = "¢")
            TextIn("Label", label, { label = it })
            Note("When periods overlap, the one that starts later wins.")
        }
    }, confirmButton = { Button(enabled = c != null && c > 0 && !to.isBefore(from), onClick = { onSave(from, to, Math.round(c!! * 10).toInt(), label) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
