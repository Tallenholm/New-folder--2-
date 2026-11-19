package com.mapledger.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapledger.app.AppContainer
import com.mapledger.app.data.db.TripStatus
import com.mapledger.app.domain.Ledger
import com.mapledger.app.domain.RatePeriod
import com.mapledger.app.ui.appViewModel
import com.mapledger.app.ui.components.*
import com.mapledger.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TaxViewModel(private val c: AppContainer) : ViewModel() {
    val year = MutableStateFlow(LocalDate.now().year)
    private val data = combine(c.trips.observeTrips(), c.finance.observeExpenses(), c.finance.observePayments(), c.finance.observeVehicles(), c.finance.observeRates()) { t, e, p, v, r ->
        Quint(t.filter { it.status == TripStatus.COMPLETED }, e, p, v, r.map { RatePeriod(LocalDate.ofEpochDay(it.fromEpochDay), LocalDate.ofEpochDay(it.toEpochDay), it.tenthsOfCent, it.label) })
    }
    val tax = combine(data, year, c.settings.settings) { d, y, s -> Ledger.taxYear(y, d.a, d.b, d.c, d.d, d.e, s.marginalTaxRate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    var message by mutableStateOf<String?>(null)

    private fun run(label: String, block: suspend () -> Any) = viewModelScope.launch {
        message = "Exporting…"
        message = runCatching { block() }.fold({ "$label exported ($it)" }, { "Export failed: ${it.message}" })
    }
    fun csv(u: Uri) = run("CSV") { "${c.transfer.exportIrsCsv(u, year.value)} trips" }
    fun xlsx(u: Uri) = run("Excel") { "${c.transfer.exportXlsx(u, year.value)} trips" }
    fun pdf(u: Uri) = run("PDF") { "${c.transfer.exportPdf(u, year.value)} trips" }
    fun gpx(u: Uri) = run("GPX") {
        val (f, t) = Ledger.yearBounds(year.value)
        val ids = c.db.trips().completedBetween(f, t).map { it.id }
        "${c.transfer.exportTripsGpx(u, ids)} tracks"
    }
    fun json(u: Uri) = run("JSON") { c.transfer.exportJson(u); "full database" }
}

data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

@Composable
fun TaxScreen(onOpenTrip: (Long) -> Unit) {
    val vm = appViewModel { TaxViewModel(it) }
    val year by vm.year.collectAsState()
    val t by vm.tax.collectAsState()
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::csv) }
    val xlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { it?.let(vm::xlsx) }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let(vm::pdf) }
    val gpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { it?.let(vm::gpx) }
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::json) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tax Center", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.year.value = year - 1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous year") }
            Text("$year", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { vm.year.value = year + 1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next year") }
        }
        val tx = t ?: return@Column
        SectionCard("Mileage") {
            KV("Total vehicle miles", Fmt.miles(tx.totalMiles))
            KV("Business (claimable)", Fmt.miles(tx.businessMiles), Good)
            KV("Personal", Fmt.miles(tx.personalMiles))
            KV("Unclassified", Fmt.miles(tx.unclassifiedMiles), if (tx.unclassifiedMiles > 0) Warn else MaterialTheme.colorScheme.onSurface)
            KV("Home travel pending review", Fmt.miles(tx.reviewMiles), if (tx.reviewMiles > 0) Warn else MaterialTheme.colorScheme.onSurface)
            Note("Home-to-mapping travel is recorded in full but excluded until you review it on each trip.")
        }
        tx.vehicles.filter { it.totalMiles > 0 || it.operatingCostsCents > 0 }.forEach { v ->
            SectionCard("${v.vehicle.name} · ${v.vehicle.deductionMethod.name.lowercase().replace('_', ' ')}") {
                KV("Business / total miles", "${Fmt.miles(v.businessMiles)} / ${Fmt.miles(v.totalMiles)}")
                KV("Standard mileage deduction", Fmt.money(v.standardDeductionCents))
                KV("Vehicle operating costs", Fmt.money(v.operatingCostsCents))
                KV("Actual-expense alternative", Fmt.money(v.actualDeductionCents))
                KV("Claimed in estimate", Fmt.money(v.claimedCents), Good)
                Note(v.methodNote)
            }
        }
        SectionCard("Deductions & estimate") {
            KV("Vehicle deduction", Fmt.money(tx.vehicleDeductionCents))
            KV("Other business expenses", Fmt.money(tx.otherDeductionCents))
            KV("Income (USD fair-market value)", Fmt.money(tx.incomeCents))
            KV("Net profit", Fmt.money(tx.netProfitCents), if (tx.netProfitCents >= 0) Good else Bad)
            KV("Self-employment tax (est.)", Fmt.money(tx.seTaxCents))
            KV("Income tax (est., Settings rate)", Fmt.money(tx.incomeTaxCents))
            Note("Rough estimate for planning. Not tax advice; depreciation, QBI, credits and state tax are not modelled.")
        }
        SectionCard("Tax-ready exports") {
            vm.message?.let { Note(it, Info) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pdf.launch("MapLedger-$year-mileage-log.pdf") }, Modifier.weight(1f)) { Text("PDF") }
                OutlinedButton(onClick = { xlsx.launch("MapLedger-$year.xlsx") }, Modifier.weight(1f)) { Text("Excel") }
                OutlinedButton(onClick = { csv.launch("MapLedger-$year-mileage-log.csv") }, Modifier.weight(1f)) { Text("CSV") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { gpx.launch("MapLedger-$year-tracks.gpx") }, Modifier.weight(1f)) { Text("GPX tracks") }
                OutlinedButton(onClick = { json.launch("MapLedger-data.json") }, Modifier.weight(1f)) { Text("JSON (all)") }
            }
            Note("Exports contain precise location history. Share them only with people you trust.")
        }
        SectionCard("Missing evidence (${tx.missing.size})") {
            if (tx.missing.isEmpty()) Note("Nothing missing.", Good)
            tx.missing.take(100).forEach { m ->
                Text("• ${m.message}", style = MaterialTheme.typography.bodySmall,
                    color = if (m.severity >= 2) Warn else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (m.tripId != null) Modifier.clickable { onOpenTrip(m.tripId) }.padding(vertical = 2.dp) else Modifier.padding(vertical = 2.dp))
            }
        }
    }
}
