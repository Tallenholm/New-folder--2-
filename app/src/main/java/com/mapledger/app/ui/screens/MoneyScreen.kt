package com.mapledger.app.ui.screens

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
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapledger.app.AppContainer
import com.mapledger.app.data.db.*
import com.mapledger.app.domain.Ledger
import com.mapledger.app.ui.appViewModel
import com.mapledger.app.ui.components.*
import com.mapledger.app.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToLong

class MoneyViewModel(private val c: AppContainer) : ViewModel() {
    val payments = c.finance.observePayments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val expenses = c.finance.observeExpenses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val vehicles = c.finance.observeVehicles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val targets = c.bee.observeTargets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trips = c.trips.observeTrips().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var message by mutableStateOf<String?>(null)
    fun save(p: Payment) = viewModelScope.launch { c.finance.savePayment(p); c.bee.reconcile() }
    fun deletePayment(id: Long) = viewModelScope.launch { c.finance.deletePayment(id); c.bee.reconcile() }
    fun save(e: Expense) = viewModelScope.launch { c.finance.saveExpense(e) }
    fun deleteExpense(id: Long) = viewModelScope.launch { c.finance.deleteExpense(id) }
    fun importPayments(u: android.net.Uri) = viewModelScope.launch {
        message = runCatching { c.transfer.importPayments(u).summary }.getOrElse { "Import failed: ${it.message}" }; c.bee.reconcile()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyScreen() {
    val vm = appViewModel { MoneyViewModel(it) }
    var tab by remember { mutableIntStateOf(0) }
    Column {
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Earnings", "Expenses", "Reports").forEachIndexed { i, s -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(s) }) }
        }
        when (tab) { 0 -> EarningsTab(vm); 1 -> ExpensesTab(vm); else -> ReportsTab(vm) }
    }
}

@Composable
private fun EarningsTab(vm: MoneyViewModel) {
    val payments by vm.payments.collectAsState()
    val targets by vm.targets.collectAsState()
    var edit by remember { mutableStateOf<Payment?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importPayments) }
    val received = payments.filter { it.status == PaymentStatus.RECEIVED }
    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { edit = Payment(date = System.currentTimeMillis(), kind = PaymentKind.BASE, currency = PaymentCurrency.USDC, amountMicros = 0, usdFmvCents = null) },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Payment") })
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard("Totals (USD fair-market value)") {
                    StatRow({ Stat("Base", Fmt.money(received.filter { it.kind == PaymentKind.BASE }.sumOf { it.usdFmvCents ?: 0 }), Modifier.weight(1f)) },
                        { Stat("Burst", Fmt.money(received.filter { it.kind == PaymentKind.BURST }.sumOf { it.usdFmvCents ?: 0 }), Modifier.weight(1f)) },
                        { Stat("Other", Fmt.money(received.filter { it.kind !in setOf(PaymentKind.BASE, PaymentKind.BURST) }.sumOf { it.usdFmvCents ?: 0 }), Modifier.weight(1f)) })
                    val noFmv = received.count { it.usdFmvCents == null }
                    if (noFmv > 0) Note("$noFmv crypto payment(s) need the USD value on the day received (taxable income = fair-market value at receipt).", Warn)
                    val pending = payments.filter { it.status == PaymentStatus.EXPECTED }
                    if (pending.isNotEmpty()) Note("${pending.size} expected payment(s) not yet received: ${Fmt.money(pending.sumOf { it.expectedUsdCents ?: 0 })}", Info)
                    OutlinedButton(onClick = { importer.launch(arrayOf("text/*", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*")) }) { Text("Import transactions (CSV / XLSX)") }
                    Note("Accepts MapLedger exports, wallet/exchange CSVs, and Solana explorer token-transfer exports. Duplicates are skipped by transaction hash.")
                    vm.message?.let { Note(it, Info) }
                }
            }
            if (payments.isEmpty()) item { EmptyState("No payments yet.") }
            items(payments, key = { it.id }) { p ->
                Card(Modifier.fillMaxWidth().clickable { edit = p }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${p.kind.name.lowercase().replaceFirstChar { it.uppercase() }} · ${Fmt.micros(p.amountMicros)} ${p.currency}", style = MaterialTheme.typography.titleSmall)
                            Text(Fmt.date(p.date) + (if (p.campaignId.isNotBlank()) " · ${p.campaignId}" else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            p.burstTargetId?.let { id -> targets.firstOrNull { it.id == id }?.let { Text("Linked to burst: ${it.name}", style = MaterialTheme.typography.bodySmall, color = Good) } }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (p.status == PaymentStatus.EXPECTED) Fmt.money(p.expectedUsdCents) else Fmt.money(p.usdFmvCents), style = MaterialTheme.typography.titleMedium,
                                color = if (p.usdFmvCents == null && p.status == PaymentStatus.RECEIVED) Warn else MaterialTheme.colorScheme.onSurface)
                            if (p.status == PaymentStatus.EXPECTED) Pill("expected", Info)
                        }
                    }
                }
            }
        }
    }
    edit?.let { p -> PaymentDialog(p, targets, onDismiss = { edit = null }, onDelete = { vm.deletePayment(p.id); edit = null }) { vm.save(it); edit = null } }
}

@Composable
fun PaymentDialog(p: Payment, targets: List<BurstTarget>, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Payment) -> Unit) {
    var date by remember { mutableStateOf(Fmt.localDate(p.date)) }
    var kind by remember { mutableStateOf(p.kind) }
    var cur by remember { mutableStateOf(p.currency) }
    var amount by remember { mutableStateOf(if (p.amountMicros > 0) Fmt.micros(p.amountMicros).replace(",", "") else "") }
    var usd by remember { mutableStateOf(p.usdFmvCents?.let { "%.2f".format(it / 100.0) } ?: "") }
    var status by remember { mutableStateOf(p.status) }
    var expected by remember { mutableStateOf(p.expectedUsdCents?.let { "%.2f".format(it / 100.0) } ?: "") }
    var campaign by remember { mutableStateOf(p.campaignId) }
    var tx by remember { mutableStateOf(p.txHash) }
    var notes by remember { mutableStateOf(p.notes) }
    var target by remember { mutableStateOf(targets.firstOrNull { it.id == p.burstTargetId }) }
    val amt = amount.toDoubleOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (p.id == 0L) "Add payment" else "Edit payment") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Date received", date, { date = it })
            Dropdown("Status", PaymentStatus.entries, status, { it.name.lowercase() }, { status = it })
            Dropdown("Type", PaymentKind.entries, kind, { it.name.lowercase() }, { kind = it })
            Dropdown("Currency", PaymentCurrency.entries, cur, { it.name }, { cur = it })
            NumberField("Amount", amount, { amount = it }, suffix = cur.name)
            NumberField("USD fair-market value at receipt", usd, { usd = it }, suffix = "USD")
            if (cur == PaymentCurrency.USDC && amt != null && usd.isBlank()) TextButton(onClick = { usd = "%.2f".format(amt) }) { Text("Use 1 USDC = $1.00") }
            if (status == PaymentStatus.EXPECTED || kind == PaymentKind.BURST) NumberField("Expected USD", expected, { expected = it }, suffix = "USD")
            TextIn("Campaign ID", campaign, { campaign = it })
            if (kind == PaymentKind.BURST) Dropdown("Link burst target", listOf<BurstTarget?>(null) + targets, target, { it?.name ?: "— none —" }, { target = it })
            TextIn("Transaction hash / reference", tx, { tx = it })
            TextIn("Notes", notes, { notes = it }, singleLine = false)
        }
    }, confirmButton = {
        Button(enabled = amt != null && amt > 0, onClick = {
            onSave(p.copy(date = Fmt.localMidnight(date) + 12 * 3_600_000L, kind = kind, currency = cur, amountMicros = (amt!! * 1_000_000).roundToLong(),
                usdFmvCents = usd.toDoubleOrNull()?.let { (it * 100).roundToLong() }, status = status,
                expectedUsdCents = expected.toDoubleOrNull()?.let { (it * 100).roundToLong() }, campaignId = campaign.trim(),
                burstTargetId = if (kind == PaymentKind.BURST) target?.id else null, txHash = tx.trim(), notes = notes))
        }) { Text("Save") }
    }, dismissButton = {
        Row { if (p.id != 0L) TextButton(onClick = onDelete) { Text("Delete", color = Bad) }; TextButton(onClick = onDismiss) { Text("Cancel") } }
    })
}

@Composable
private fun ExpensesTab(vm: MoneyViewModel) {
    val expenses by vm.expenses.collectAsState()
    val vehicles by vm.vehicles.collectAsState()
    var edit by remember { mutableStateOf<Expense?>(null) }
    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { edit = Expense(vehicleId = vehicles.firstOrNull { it.isPrimary }?.id, date = Fmt.localMidnight(LocalDate.now()), category = ExpenseCategory.GAS, amountCents = 0) },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Expense") })
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val year = LocalDate.now().year
                val ye = expenses.filter { Fmt.localDate(it.date).year == year }
                SectionCard("$year by category") {
                    ExpenseCategory.entries.forEach { c -> val s = ye.filter { it.category == c }.sumOf { it.amountCents }; if (s > 0) KV(c.label, Fmt.money(s)) }
                    KV("Total", Fmt.money(ye.sumOf { it.amountCents }))
                    Note("Under the standard mileage method, gas, repairs, tires, insurance and rental costs are tracked but not deducted separately. Parking, tolls, equipment and phone are deductible either way.")
                }
            }
            if (expenses.isEmpty()) item { EmptyState("No expenses yet.") }
            items(expenses, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth().clickable { edit = e }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.category.label + if (e.vendor.isNotBlank()) " · ${e.vendor}" else "", style = MaterialTheme.typography.titleSmall)
                            Text(Fmt.date(e.date) + (vehicles.firstOrNull { it.id == e.vehicleId }?.let { " · ${it.name}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (e.receiptPhoto != null) Icon(Icons.Default.Receipt, "Receipt", tint = Good) else Pill("no receipt", Warn)
                        Spacer(Modifier.width(8.dp))
                        Text(Fmt.money(e.amountCents), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
    edit?.let { e -> ExpenseDialog(e, vehicles, onDismiss = { edit = null }, onDelete = { vm.deleteExpense(e.id); edit = null }) { vm.save(it); edit = null } }
}

@Composable
private fun ExpenseDialog(e: Expense, vehicles: List<Vehicle>, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Expense) -> Unit) {
    var date by remember { mutableStateOf(Fmt.localDate(e.date)) }
    var cat by remember { mutableStateOf(e.category) }
    var amount by remember { mutableStateOf(if (e.amountCents > 0) "%.2f".format(e.amountCents / 100.0) else "") }
    var vendor by remember { mutableStateOf(e.vendor) }
    var vehicle by remember { mutableStateOf(vehicles.firstOrNull { it.id == e.vehicleId }) }
    var pct by remember { mutableStateOf(e.businessPct.toString()) }
    var gallons by remember { mutableStateOf(e.gallons?.toString() ?: "") }
    var odo by remember { mutableStateOf(e.odometer?.toString() ?: "") }
    var photo by remember { mutableStateOf(e.receiptPhoto) }
    var notes by remember { mutableStateOf(e.notes) }
    val amt = amount.toDoubleOrNull(); val p = pct.toIntOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (e.id == 0L) "Add expense" else "Edit expense") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Date", date, { date = it })
            Dropdown("Category", ExpenseCategory.entries, cat, { it.label }, { cat = it })
            NumberField("Amount", amount, { amount = it }, suffix = "USD")
            TextIn("Vendor", vendor, { vendor = it })
            Dropdown("Vehicle", listOf<Vehicle?>(null) + vehicles, vehicle, { it?.name ?: "— not vehicle-specific —" }, { vehicle = it })
            if (cat.vehicleOperating && vehicle == null) Note("Vehicle costs need a vehicle so they are allocated correctly.", Warn)
            if (!cat.vehicleOperating) NumberField("Business use %", pct, { pct = it }, decimal = false, suffix = "%")
            if (cat == ExpenseCategory.GAS) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Gallons", gallons, { gallons = it }, Modifier.weight(1f)); NumberField("Odometer", odo, { odo = it }, Modifier.weight(1f))
            }
            PhotoEvidence("Receipt", photo, "receipt") { photo = it }
            TextIn("Notes", notes, { notes = it }, singleLine = false)
        }
    }, confirmButton = {
        Button(enabled = amt != null && amt > 0 && p != null && p in 0..100, onClick = {
            onSave(e.copy(date = Fmt.localMidnight(date), category = cat, amountCents = (amt!! * 100).roundToLong(), vendor = vendor.trim(), vehicleId = vehicle?.id,
                businessPct = if (cat.vehicleOperating) 100 else p!!, gallons = gallons.toDoubleOrNull(), odometer = odo.toDoubleOrNull(), receiptPhoto = photo, notes = notes))
        }) { Text("Save") }
    }, dismissButton = {
        Row { if (e.id != 0L) TextButton(onClick = onDelete) { Text("Delete", color = Bad) }; TextButton(onClick = onDismiss) { Text("Cancel") } }
    })
}

@Composable
private fun ReportsTab(vm: MoneyViewModel) {
    val trips by vm.trips.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val payments by vm.payments.collectAsState()
    var period by remember { mutableStateOf(Ledger.Period.WEEK) }
    val rows = remember(trips, expenses, payments, period) { Ledger.report(period, trips.filter { it.status == TripStatus.COMPLETED }, expenses, payments) }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Ledger.Period.entries.forEachIndexed { i, p ->
                    SegmentedButton(selected = period == p, onClick = { period = p }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) }
                }
            }
            Note("Cash view: income received minus business share of money spent. Hours = non-personal trip time excluding pauses.")
        }
        if (rows.isEmpty()) item { EmptyState("Nothing to report yet.") }
        items(rows, key = { it.start.toString() }) { r ->
            SectionCard(r.label) {
                StatRow({ Stat("Miles", Fmt.miles(r.miles), Modifier.weight(1f), sub = "${Fmt.miles(r.businessMiles)} business") },
                    { Stat("Hours", "%.1f".format(r.hours), Modifier.weight(1f)) })
                StatRow({ Stat("Income", Fmt.money(r.incomeCents), Modifier.weight(1f), sub = "burst ${Fmt.money(r.burstCents)}") },
                    { Stat("Spent", Fmt.money(r.expenseCents), Modifier.weight(1f)) })
                StatRow({ Stat("Net", Fmt.money(r.netCents), Modifier.weight(1f), color = if (r.netCents >= 0) Good else Bad) },
                    { Stat("Net/hr · /mi", "${Fmt.money(r.netPerHour)} · ${Fmt.money(r.netPerMile)}", Modifier.weight(1f)) })
            }
        }
    }
}
