package com.mapledger.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapledger.app.AppContainer
import com.mapledger.app.data.db.RecordSource
import com.mapledger.app.data.db.Trip
import com.mapledger.app.data.db.TripClass
import com.mapledger.app.data.db.TripStatus
import com.mapledger.app.data.db.Vehicle
import com.mapledger.app.domain.Ledger
import com.mapledger.app.ui.appViewModel
import com.mapledger.app.ui.components.*
import com.mapledger.app.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TripsViewModel(private val c: AppContainer) : ViewModel() {
    val trips = c.trips.observeTrips().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val vehicles = c.finance.observeVehicles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun addManual(v: Vehicle, date: LocalDate, start: LocalTime, end: LocalTime, miles: Double?, so: Double?, eo: Double?, from: String, to: String, purpose: String, cls: TripClass) =
        viewModelScope.launch {
            val z = ZoneId.systemDefault()
            c.trips.addManualTrip(v.id, date.atTime(start).atZone(z).toInstant().toEpochMilli(), date.atTime(end).atZone(z).toInstant().toEpochMilli(),
                miles, so, eo, from, to, purpose, cls)
        }
}

enum class TripFilter(val label: String) { ALL("All"), UNCLASSIFIED("To classify"), REVIEW("Home review"), BUSINESS("Business"), PERSONAL("Personal") }

@Composable
fun TripsScreen(onOpen: (Long) -> Unit) {
    val vm = appViewModel { TripsViewModel(it) }
    val trips by vm.trips.collectAsState()
    val vehicles by vm.vehicles.collectAsState()
    var filter by remember { mutableStateOf(TripFilter.ALL) }
    var showAdd by remember { mutableStateOf(false) }
    val shown = trips.filter { t ->
        when (filter) {
            TripFilter.ALL -> true
            TripFilter.UNCLASSIFIED -> t.classification == TripClass.UNCLASSIFIED && t.status == TripStatus.COMPLETED
            TripFilter.REVIEW -> t.needsTaxReview
            TripFilter.BUSINESS -> t.classification == TripClass.BUSINESS || t.classification == TripClass.MIXED
            TripFilter.PERSONAL -> t.classification == TripClass.PERSONAL
        }
    }
    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { showAdd = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Manual trip") })
    }) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TripFilter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
            }
            if (shown.isEmpty()) EmptyState(if (trips.isEmpty()) "No trips yet. Start a mapping session on the Drive tab." else "No trips match this filter.")
            LazyColumn(contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { t -> TripCard(t, vehicles.firstOrNull { it.id == t.vehicleId }) { onOpen(t.id) } }
            }
        }
    }
    if (showAdd) ManualTripDialog(vehicles, onDismiss = { showAdd = false }) { v, d, s, e, mi, so, eo, from, to, p, cls ->
        vm.addManual(v, d, s, e, mi, so, eo, from, to, p, cls); showAdd = false
    }
}

@Composable
fun ClassPill(t: Trip) = when {
    t.status != TripStatus.COMPLETED -> Pill(t.status.name.lowercase(), Warn)
    t.classification == TripClass.UNCLASSIFIED -> Pill("Unclassified", Warn)
    t.classification == TripClass.BUSINESS -> Pill("Business", Good)
    t.classification == TripClass.MIXED -> Pill("Mixed", Info)
    else -> Pill("Personal", MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TripCard(t: Trip, v: Vehicle?, onClick: () -> Unit) {
    val (miles, basis) = Ledger.totalMiles(t)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.date(t.startTime), style = MaterialTheme.typography.titleSmall)
                    Text("${Fmt.time(t.startTime)} – ${Fmt.time(t.endTime)} · ${v?.name ?: "?"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.miles(miles, 2), style = MaterialTheme.typography.titleLarge)
                    Text(basis.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (t.startLabel.isNotBlank() || t.endLabel.isNotBlank()) Text("${t.startLabel.ifBlank { "?" }} → ${t.endLabel.ifBlank { "?" }}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ClassPill(t)
                if (t.needsTaxReview) Pill("Home travel – review", Warn, Icons.Default.Home)
                if (t.source != RecordSource.LIVE_GPS) Pill(t.source.name.lowercase().replace('_', ' '), Info)
                Text("conf ${t.confidence}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ManualTripDialog(vehicles: List<Vehicle>, onDismiss: () -> Unit,
    onSave: (Vehicle, LocalDate, LocalTime, LocalTime, Double?, Double?, Double?, String, String, String, TripClass) -> Unit) {
    var v by remember { mutableStateOf(vehicles.firstOrNull()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var start by remember { mutableStateOf("09:00") }; var end by remember { mutableStateOf("17:00") }
    var miles by remember { mutableStateOf("") }; var so by remember { mutableStateOf("") }; var eo by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }; var to by remember { mutableStateOf("") }; var purpose by remember { mutableStateOf("") }
    var cls by remember { mutableStateOf(TripClass.UNCLASSIFIED) }
    val st = runCatching { LocalTime.parse(start) }.getOrNull(); val et = runCatching { LocalTime.parse(end) }.getOrNull()
    val ok = v != null && st != null && et != null && (miles.toDoubleOrNull() != null || (so.toDoubleOrNull() != null && eo.toDoubleOrNull() != null))
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Manual trip") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Note("Manual entries have no GPS evidence and get low confidence. Odometer readings strengthen them.", Warn)
            v?.let { Dropdown("Vehicle", vehicles, it, { x -> x.name }, { x -> v = x }) }
            DateField("Date", date, { date = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextIn("Start (HH:mm)", start, { start = it }, Modifier.weight(1f)); TextIn("End (HH:mm)", end, { end = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Odo start", so, { so = it }, Modifier.weight(1f)); NumberField("Odo end", eo, { eo = it }, Modifier.weight(1f))
            }
            NumberField("Miles (if no odometer)", miles, { miles = it }, suffix = "mi")
            TextIn("From", from, { from = it }); TextIn("To", to, { to = it })
            Dropdown("Classification", TripClass.entries, cls, { it.name.lowercase() }, { cls = it })
            TextIn("Business purpose", purpose, { purpose = it })
        }
    }, confirmButton = {
        Button(enabled = ok, onClick = { onSave(v!!, date, st!!, et!!, miles.toDoubleOrNull(), so.toDoubleOrNull(), eo.toDoubleOrNull(), from, to, purpose, cls) }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
