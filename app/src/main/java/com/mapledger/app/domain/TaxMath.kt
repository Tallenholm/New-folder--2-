package com.mapledger.app.domain

import java.time.LocalDate

/** IRS standard mileage rate in tenths of a cent (72.5¢ = 725) valid for a closed date range. */
data class RatePeriod(val from: LocalDate, val to: LocalDate, val tenthsOfCent: Int, val label: String = "") {
    val dollarsPerMile: Double get() = tenthsOfCent / 1000.0
    fun covers(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
}

object TaxMath {
    /** Defaults seeded on first run; editable in Settings → Mileage rates. */
    val DEFAULT_RATES = listOf(
        RatePeriod(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 700, "IRS 2025 business"),
        RatePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), 725, "IRS 2026 business (Jan–Jun)"),
        RatePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), 760, "IRS 2026 business (Jul–Dec)"),
    )

    fun rateFor(date: LocalDate, rates: List<RatePeriod>): RatePeriod? = rates.lastOrNull { it.covers(date) }

    /** Deduction in cents for [businessMiles] driven on [date]; null when no rate is configured. */
    fun mileageDeductionCents(businessMiles: Double, date: LocalDate, rates: List<RatePeriod>): Long? {
        val r = rateFor(date, rates) ?: return null
        return Math.round(businessMiles * r.tenthsOfCent / 10.0)
    }

    /** Self-employment tax estimate (Schedule SE): 92.35% of net earnings × 15.3%. Rough estimate only. */
    fun selfEmploymentTaxCents(netProfitCents: Long): Long =
        if (netProfitCents <= 0) 0 else Math.round(netProfitCents * 0.9235 * 0.153)

    fun incomeTaxEstimateCents(netProfitCents: Long, marginalRate: Double): Long {
        if (netProfitCents <= 0) return 0
        // Half of SE tax is deductible from income.
        val adj = netProfitCents - selfEmploymentTaxCents(netProfitCents) / 2
        return Math.round(adj.coerceAtLeast(0) * marginalRate)
    }
}
