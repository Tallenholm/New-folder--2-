package com.mapledger.app.domain

import com.mapledger.app.data.db.DeductionMethod
import com.mapledger.app.data.db.Expense
import com.mapledger.app.data.db.ExpenseCategory
import com.mapledger.app.data.db.Payment
import com.mapledger.app.data.db.PaymentStatus
import com.mapledger.app.data.db.RecordSource
import com.mapledger.app.data.db.Trip
import com.mapledger.app.data.db.TripClass
import com.mapledger.app.data.db.Vehicle
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

/** Business rules shared by the dashboard, Tax Center, reports and exports. */
object Ledger {

    enum class MilesBasis { ADJUSTED, ODOMETER, GPS }

    /** Total vehicle miles for a trip: user adjustment > odometer difference > verified GPS. */
    fun totalMiles(t: Trip): Pair<Double, MilesBasis> {
        t.adjustedMiles?.let { return it to MilesBasis.ADJUSTED }
        val so = t.startOdometer; val eo = t.endOdometer
        if (so != null && eo != null && eo >= so) return (eo - so) to MilesBasis.ODOMETER
        return GeoMath.metersToMiles(t.gpsMeters) to MilesBasis.GPS
    }

    /** Business miles claimed for the trip (0 unless the user classified it). */
    fun businessMiles(t: Trip): Double = when (t.classification) {
        TripClass.BUSINESS -> if (t.businessMiles > 0) t.businessMiles else totalMiles(t).first
        TripClass.MIXED -> t.businessMiles
        else -> 0.0
    }

    fun personalMiles(t: Trip): Double = when (t.classification) {
        TripClass.PERSONAL -> totalMiles(t).first
        TripClass.MIXED -> if (t.personalMiles > 0) t.personalMiles else (totalMiles(t).first - t.businessMiles).coerceAtLeast(0.0)
        TripClass.BUSINESS -> (totalMiles(t).first - businessMiles(t)).coerceAtLeast(0.0)
        else -> 0.0
    }

    /** Evidence confidence 0–100 shown in the IRS log. */
    fun confidence(t: Trip): Int {
        var c = when (t.source) {
            RecordSource.LIVE_GPS -> 70
            RecordSource.GPX_IMPORT -> 55
            RecordSource.TIMELINE_IMPORT -> 40
            RecordSource.CSV_IMPORT -> 35
            RecordSource.MANUAL -> 25
        }
        if (t.startOdometer != null && t.endOdometer != null) c += 20
        if (t.startOdometerPhoto != null || t.endOdometerPhoto != null) c += 5
        if (t.gapMeters > 0 && t.gpsMeters > 0) c -= ((t.gapMeters / (t.gpsMeters + t.gapMeters)) * 40).toInt()
        if (t.adjustedMiles != null) c -= 10
        return c.coerceIn(0, 100)
    }

    fun localDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    fun yearBounds(year: Int, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> =
        LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli() to
            LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()

    data class VehicleTax(
        val vehicle: Vehicle,
        val totalMiles: Double,
        val businessMiles: Double,
        val reviewMiles: Double,
        val standardDeductionCents: Long,
        val operatingCostsCents: Long,
        val actualDeductionCents: Long,
        val claimedCents: Long,
        val methodNote: String,
    )

    data class MissingEvidence(val severity: Int, val message: String, val tripId: Long? = null, val expenseId: Long? = null, val paymentId: Long? = null)

    data class TaxYear(
        val year: Int,
        val totalMiles: Double,
        val businessMiles: Double,
        val personalMiles: Double,
        val unclassifiedMiles: Double,
        val reviewMiles: Double,
        val homeTravelMiles: Double,
        val vehicles: List<VehicleTax>,
        val vehicleDeductionCents: Long,
        val otherDeductionCents: Long,
        val incomeCents: Long,
        val incomeMissingFmv: Int,
        val netProfitCents: Long,
        val seTaxCents: Long,
        val incomeTaxCents: Long,
        val missing: List<MissingEvidence>,
    ) { val totalDeductionCents get() = vehicleDeductionCents + otherDeductionCents }

    fun taxYear(
        year: Int, trips: List<Trip>, expenses: List<Expense>, payments: List<Payment>, vehicles: List<Vehicle>,
        rates: List<RatePeriod>, marginalRate: Double, zone: ZoneId = ZoneId.systemDefault(),
    ): TaxYear {
        val ys = trips.filter { localDate(it.startTime, zone).year == year }
        val ye = expenses.filter { localDate(it.date, zone).year == year }
        val yp = payments.filter { localDate(it.date, zone).year == year && it.status == PaymentStatus.RECEIVED }
        val missing = mutableListOf<MissingEvidence>()

        val vtax = vehicles.map { v ->
            val vt = ys.filter { it.vehicleId == v.id }
            val total = vt.sumOf { totalMiles(it).first }
            val claimable = vt.filter { !it.needsTaxReview }
            val biz = claimable.sumOf { businessMiles(it) }
            val review = vt.filter { it.needsTaxReview }.sumOf { businessMiles(it).takeIf { b -> b > 0 } ?: totalMiles(it).first }
            var std = 0L
            claimable.forEach { t ->
                val bm = businessMiles(t)
                if (bm > 0) {
                    val d = localDate(t.startTime, zone)
                    val cents = TaxMath.mileageDeductionCents(bm, d, rates)
                    if (cents == null) missing += MissingEvidence(2, "No IRS mileage rate configured for $d", tripId = t.id) else std += cents
                }
            }
            val ops = ye.filter { it.vehicleId == v.id && it.category.vehicleOperating }.sumOf { it.amountCents }
            val bizRatio = if (total > 0) (biz / total).coerceIn(0.0, 1.0) else 0.0
            val actual = (ops * bizRatio).roundToLong()
            val (claimed, note) = when (v.deductionMethod) {
                DeductionMethod.STANDARD_MILEAGE -> std to "Standard mileage: gas, repairs, tires, insurance & rental costs are NOT deducted separately."
                DeductionMethod.ACTUAL_EXPENSES -> actual to "Actual expenses × ${"%.0f".format(bizRatio * 100)}% business use. Mileage rate not applied. Depreciation not computed — ask your tax preparer."
                DeductionMethod.UNDECIDED -> std to "Method not chosen: estimate uses standard mileage. Actual-expense alternative shown for comparison."
            }
            if (v.deductionMethod == DeductionMethod.UNDECIDED && total > 0) missing += MissingEvidence(1, "Choose a deduction method for ${v.name}")
            VehicleTax(v, total, biz, review, std, ops, actual, claimed, note)
        }

        // Non-vehicle-operating expenses (parking/tolls, equipment, phone…) are deductible under both methods.
        val other = ye.filter { !it.category.vehicleOperating }.sumOf { (it.amountCents * it.businessPct / 100.0).roundToLong() }
        // Vehicle-operating expenses not tied to a vehicle can't be allocated safely.
        ye.filter { it.category.vehicleOperating && it.vehicleId == null }.forEach {
            missing += MissingEvidence(1, "${it.category.label} expense on ${localDate(it.date, zone)} has no vehicle — not counted", expenseId = it.id)
        }

        ys.forEach { t ->
            val d = localDate(t.startTime, zone)
            if (t.classification == TripClass.UNCLASSIFIED) missing += MissingEvidence(2, "Trip on $d is unclassified", tripId = t.id)
            if (t.classification in setOf(TripClass.BUSINESS, TripClass.MIXED) && t.purpose.isBlank())
                missing += MissingEvidence(2, "Business trip on $d has no business purpose", tripId = t.id)
            if (t.startOdometer == null || t.endOdometer == null) missing += MissingEvidence(1, "Trip on $d is missing odometer readings", tripId = t.id)
            if (t.needsTaxReview) missing += MissingEvidence(2, "Trip on $d includes home travel — review commuting rules", tripId = t.id)
            if (t.gapMeters > 0) missing += MissingEvidence(1, "Trip on $d has ${"%.1f".format(GeoMath.metersToMiles(t.gapMeters))} mi of unverified GPS gaps", tripId = t.id)
        }
        ye.filter { it.receiptPhoto == null }.forEach {
            missing += MissingEvidence(0, "No receipt photo: ${it.category.label} ${"$%.2f".format(it.amountCents / 100.0)} on ${localDate(it.date, zone)}", expenseId = it.id)
        }
        val noFmv = yp.filter { it.usdFmvCents == null }
        noFmv.forEach { missing += MissingEvidence(2, "Payment on ${localDate(it.date, zone)} has no USD fair-market value", paymentId = it.id) }

        val income = yp.sumOf { it.usdFmvCents ?: 0 }
        val vehDed = vtax.sumOf { it.claimedCents }
        val net = income - vehDed - other
        return TaxYear(
            year = year,
            totalMiles = ys.sumOf { totalMiles(it).first },
            businessMiles = vtax.sumOf { it.businessMiles },
            personalMiles = ys.sumOf { personalMiles(it) },
            unclassifiedMiles = ys.filter { it.classification == TripClass.UNCLASSIFIED }.sumOf { totalMiles(it).first },
            reviewMiles = vtax.sumOf { it.reviewMiles },
            homeTravelMiles = ys.filter { it.startsAtHome || it.endsAtHome }.sumOf { totalMiles(it).first },
            vehicles = vtax,
            vehicleDeductionCents = vehDed,
            otherDeductionCents = other,
            incomeCents = income,
            incomeMissingFmv = noFmv.size,
            netProfitCents = net,
            seTaxCents = TaxMath.selfEmploymentTaxCents(net),
            incomeTaxCents = TaxMath.incomeTaxEstimateCents(net, marginalRate),
            missing = missing.sortedByDescending { it.severity },
        )
    }

    enum class Period { WEEK, MONTH, YEAR }

    data class PeriodRow(
        val start: LocalDate, val label: String, val miles: Double, val businessMiles: Double, val hours: Double,
        val incomeCents: Long, val expenseCents: Long, val burstCents: Long,
    ) {
        val netCents get() = incomeCents - expenseCents
        val netPerHour get() = if (hours > 0) netCents / 100.0 / hours else null
        val netPerMile get() = if (miles > 0) netCents / 100.0 / miles else null
    }

    fun periodStart(d: LocalDate, p: Period): LocalDate = when (p) {
        Period.WEEK -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Period.MONTH -> d.withDayOfMonth(1)
        Period.YEAR -> d.withDayOfYear(1)
    }

    /** Cash-basis profitability report. Expenses here are actual cash spent (not the tax deduction). */
    fun report(p: Period, trips: List<Trip>, expenses: List<Expense>, payments: List<Payment>, zone: ZoneId = ZoneId.systemDefault()): List<PeriodRow> {
        val keys = (trips.map { localDate(it.startTime, zone) } + expenses.map { localDate(it.date, zone) } + payments.map { localDate(it.date, zone) })
            .map { periodStart(it, p) }.toSortedSet().reversed()
        return keys.map { k ->
            val tt = trips.filter { periodStart(localDate(it.startTime, zone), p) == k }
            val ee = expenses.filter { periodStart(localDate(it.date, zone), p) == k }
            val pp = payments.filter { it.status == PaymentStatus.RECEIVED && periodStart(localDate(it.date, zone), p) == k }
            val hours = tt.filter { it.classification != TripClass.PERSONAL }.sumOf { ((it.endTime ?: it.startTime) - it.startTime - it.pausedMs).coerceAtLeast(0) } / 3_600_000.0
            val label = when (p) {
                Period.WEEK -> "Week of $k"
                Period.MONTH -> "${k.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${k.year}"
                Period.YEAR -> "${k.year}"
            }
            PeriodRow(k, label, tt.sumOf { totalMiles(it).first }, tt.filter { !it.needsTaxReview }.sumOf { businessMiles(it) }, hours,
                pp.sumOf { it.usdFmvCents ?: 0 }, ee.sumOf { (it.amountCents * it.businessPct / 100.0).roundToLong() },
                pp.filter { it.kind == com.mapledger.app.data.db.PaymentKind.BURST }.sumOf { it.usdFmvCents ?: 0 })
        }
    }

    /** Bee Maps road-goal history seeded at first launch. Distances are goals, not odometer miles. */
    val HISTORICAL_GOALS: List<Pair<LocalDate, Double>> = listOf(
        LocalDate.of(2026, 10, 5) to 70.7,
        LocalDate.of(2026, 10, 12) to 75.8,
        LocalDate.of(2026, 10, 19) to 173.1,
        LocalDate.of(2026, 10, 26) to 377.0,
        LocalDate.of(2026, 11, 2) to 1208.7,
        LocalDate.of(2026, 11, 9) to 1712.0,
        LocalDate.of(2026, 11, 16) to 8.2,
    )

    /** Manteno, Illinois (village centre). Approximate until the user sets their exact home. */
    const val MANTENO_LAT = 41.2506
    const val MANTENO_LON = -87.8314
}
