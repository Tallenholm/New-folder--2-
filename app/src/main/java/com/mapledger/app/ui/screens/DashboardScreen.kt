package com.mapledger.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapledger.app.AppContainer
import com.mapledger.app.data.db.TripClass
import com.mapledger.app.data.db.TripStatus
import com.mapledger.app.domain.GeoMath
import com.mapledger.app.domain.Ledger
import com.mapledger.app.domain.RatePeriod
import com.mapledger.app.ui.appViewModel
import com.mapledger.app.ui.components.*
import com.mapledger.app.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class DashboardViewModel(c: AppContainer) : ViewModel() {
    private val year = LocalDate.now().year
    private val rates = c.finance.observeRates().map { list -> list.map { RatePeriod(LocalDate.ofEpochDay(it.fromEpochDay), LocalDate.ofEpochDay(it.toEpochDay), it.tenthsOfCent, it.label) } }
    private val base = combine(c.trips.observeTrips(), c.finance.observeExpenses(), c.finance.observePayments(), c.finance.observeVehicles(), rates) { t, e, p, v, r ->
        arrayOf(t, e, p, v, r)
    }
    @Suppress("UNCHECKED_CAST")
    val state = combine(base, c.settings.settings, c.bee.observeGoals(), c.bee.observeTargets()) { arr, s, goals, targets ->
        val trips = (arr[0] as List<com.mapledger.app.data.db.Trip>).filter { it.status == TripStatus.COMPLETED }
        val tax = Ledger.taxYear(year, trips, arr[1] as List<com.mapledger.app.data.db.Expense>, arr[2] as List<com.mapledger.app.data.db.Payment>,
            arr[3] as List<com.mapledger.app.data.db.Vehicle>, arr[4] as List<RatePeriod>, s.marginalTaxRate)
        val week = Ledger.report(Ledger.Period.WEEK, trips, arr[1] as List<com.mapledger.app.data.db.Expense>, arr[2] as List<com.mapledger.app.data.db.Payment>).firstOrNull()
        DashState(year, tax, week, trips.count { it.classification == TripClass.UNCLASSIFIED }, goals.sumOf { it.distanceKm },
            targets.count { it.status == com.mapledger.app.data.db.BurstStatus.MISSING_PAYMENT }, s.homeApproximate,
            (arr[0] as List<com.mapledger.app.data.db.Trip>).any { it.status != TripStatus.COMPLETED })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

data class DashState(val year: Int, val tax: Ledger.TaxYear, val week: Ledger.PeriodRow?, val unclassified: Int, val goalKm: Double,
                     val missingBursts: Int, val homeApprox: Boolean, val activeTrip: Boolean)

@Composable
fun DashboardScreen(onNavigate: (String) -> Unit) {
    val vm = appViewModel { DashboardViewModel(it) }
    val s by vm.state.collectAsState()
    val st = s ?: return EmptyState("Loading…")
    val t = st.tax
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MapLedger", style = MaterialTheme.typography.headlineSmall, color = Honey)
        if (st.activeTrip) SectionCard("Trip in progress") { Button(onClick = { onNavigate("drive") }) { Text("Open Live Drive") } }
        SectionCard("${st.year} mileage") {
            StatRow({ Stat("Total", Fmt.miles(t.totalMiles), Modifier.weight(1f)) }, { Stat("Business", Fmt.miles(t.businessMiles), Modifier.weight(1f), color = Good) })
            StatRow({ Stat("Personal", Fmt.miles(t.personalMiles), Modifier.weight(1f)) },
                { Stat("Needs review", Fmt.miles(t.reviewMiles + t.unclassifiedMiles), Modifier.weight(1f), color = if (t.reviewMiles + t.unclassifiedMiles > 0) Warn else MaterialTheme.colorScheme.onSurface) })
            if (st.unclassified > 0) TextButton(onClick = { onNavigate("trips") }) { Text("Classify ${st.unclassified} trip(s) →") }
        }
        SectionCard("${st.year} money") {
            StatRow({ Stat("Earnings", Fmt.money(t.incomeCents), Modifier.weight(1f)) }, { Stat("Deductions", Fmt.money(t.totalDeductionCents), Modifier.weight(1f)) })
            StatRow({ Stat("Net profit", Fmt.money(t.netProfitCents), Modifier.weight(1f), color = if (t.netProfitCents >= 0) Good else Bad) },
                { Stat("Est. tax", Fmt.money(t.seTaxCents + t.incomeTaxCents), Modifier.weight(1f), sub = "SE ${Fmt.money(t.seTaxCents)} + income") })
            if (t.incomeMissingFmv > 0) Note("${t.incomeMissingFmv} payment(s) missing USD value — totals are understated.", Warn)
            Note("Estimates only. Confirm with a tax professional.")
        }
        st.week?.let { w ->
            SectionCard(w.label) {
                StatRow({ Stat("Miles", Fmt.miles(w.miles), Modifier.weight(1f)) }, { Stat("Hours", "%.1f".format(w.hours), Modifier.weight(1f)) })
                StatRow({ Stat("Net/hour", Fmt.money(w.netPerHour), Modifier.weight(1f)) }, { Stat("Net/mile", Fmt.money(w.netPerMile), Modifier.weight(1f)) })
            }
        }
        SectionCard("Bee Maps") {
            KV("Historical road goals (not odometer)", "%.1f km · %.1f mi".format(st.goalKm, GeoMath.kmToMiles(st.goalKm)))
            if (st.missingBursts > 0) Note("${st.missingBursts} passed burst target(s) have no payment yet.", Warn)
            TextButton(onClick = { onNavigate("bee") }) { Text("Open Bee Maps →") }
        }
        SectionCard("Evidence checklist") {
            if (st.homeApprox) Note("Home base is approximate (Manteno village centre). Set your exact home in Settings for accurate home-travel detection.", Warn)
            val top = t.missing.take(5)
            if (top.isEmpty()) Note("No open evidence items.", Good)
            top.forEach { Note("• ${it.message}", if (it.severity >= 2) Warn else MaterialTheme.colorScheme.onSurfaceVariant) }
            if (t.missing.size > 5) TextButton(onClick = { onNavigate("tax") }) { Text("See all ${t.missing.size} in Tax Center →") }
        }
    }
}
