package com.mapledger.app.domain

import com.mapledger.app.data.db.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LedgerTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun ms(d: LocalDate, h: Int = 10) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private val car = Vehicle(id = 1, name = "Elantra", year = 2025, make = "Hyundai", model = "Elantra Hybrid", deductionMethod = DeductionMethod.STANDARD_MILEAGE)

    private fun trip(id: Long, d: LocalDate, miles: Double, cls: TripClass, review: Boolean = false, purpose: String = "Mapping", vehicle: Long = 1) =
        Trip(id = id, vehicleId = vehicle, status = TripStatus.COMPLETED, source = RecordSource.LIVE_GPS, startTime = ms(d), endTime = ms(d, 14),
            gpsMeters = GeoMath.milesToMeters(miles), classification = cls, purpose = purpose, needsTaxReview = review,
            startsAtHome = review, startOdometer = 1000.0, endOdometer = 1000.0 + miles)

    @Test fun rate2026SplitsOnJulyFirst() {
        val r = TaxMath.DEFAULT_RATES
        assertEquals(725, TaxMath.rateFor(LocalDate.of(2026, 6, 30), r)!!.tenthsOfCent)
        assertEquals(760, TaxMath.rateFor(LocalDate.of(2026, 7, 1), r)!!.tenthsOfCent)
        assertEquals(7250L, TaxMath.mileageDeductionCents(100.0, LocalDate.of(2026, 3, 1), r))
        assertEquals(7600L, TaxMath.mileageDeductionCents(100.0, LocalDate.of(2026, 10, 1), r))
        assertNull(TaxMath.mileageDeductionCents(100.0, LocalDate.of(2024, 3, 1), r))
    }

    @Test fun homeTravelIsFlaggedNotDeducted() {
        val trips = listOf(
            trip(1, LocalDate.of(2026, 10, 5), 40.0, TripClass.BUSINESS, review = true),
            trip(2, LocalDate.of(2026, 10, 6), 10.0, TripClass.BUSINESS, review = false),
        )
        val y = Ledger.taxYear(2026, trips, emptyList(), emptyList(), listOf(car), TaxMath.DEFAULT_RATES, 0.12, zone)
        assertEquals(50.0, y.totalMiles, 1e-6)
        assertEquals(10.0, y.businessMiles, 1e-6)
        assertEquals(40.0, y.reviewMiles, 1e-6)
        assertEquals(760L, y.vehicleDeductionCents) // 10 mi × 76¢
        assertTrue(y.missing.any { it.message.contains("home travel") })
    }

    @Test fun unclassifiedTripsAreNeverDeducted() {
        val y = Ledger.taxYear(2026, listOf(trip(1, LocalDate.of(2026, 11, 1), 25.0, TripClass.UNCLASSIFIED)), emptyList(), emptyList(), listOf(car), TaxMath.DEFAULT_RATES, 0.12, zone)
        assertEquals(0L, y.vehicleDeductionCents)
        assertEquals(25.0, y.unclassifiedMiles, 1e-6)
    }

    @Test fun standardMileagePreventsDoubleCountingVehicleCosts() {
        val d = LocalDate.of(2026, 8, 1)
        val exp = listOf(
            Expense(id = 1, vehicleId = 1, date = ms(d), category = ExpenseCategory.GAS, amountCents = 5000),
            Expense(id = 2, vehicleId = 1, date = ms(d), category = ExpenseCategory.TIRES, amountCents = 60000),
            Expense(id = 3, vehicleId = 1, date = ms(d), category = ExpenseCategory.PARKING_TOLLS, amountCents = 800),
            Expense(id = 4, vehicleId = null, date = ms(d), category = ExpenseCategory.PHONE_DATA, amountCents = 6000, businessPct = 50),
        )
        val trips = listOf(trip(1, d, 100.0, TripClass.BUSINESS))
        val std = Ledger.taxYear(2026, trips, exp, emptyList(), listOf(car), TaxMath.DEFAULT_RATES, 0.12, zone)
        assertEquals(7600L, std.vehicleDeductionCents)          // rate only; gas & tires excluded
        assertEquals(800L + 3000L, std.otherDeductionCents)      // tolls + 50% phone

        val actual = Ledger.taxYear(2026, trips, exp, emptyList(), listOf(car.copy(deductionMethod = DeductionMethod.ACTUAL_EXPENSES)), TaxMath.DEFAULT_RATES, 0.12, zone)
        assertEquals(65000L, actual.vehicleDeductionCents)       // 100% business use × (gas + tires); no mileage rate
        assertEquals(800L + 3000L, actual.otherDeductionCents)
    }

    @Test fun actualExpensesUseBusinessUseRatio() {
        val d = LocalDate.of(2026, 8, 1)
        val trips = listOf(trip(1, d, 75.0, TripClass.BUSINESS), trip(2, d.plusDays(1), 25.0, TripClass.PERSONAL, purpose = ""))
        val exp = listOf(Expense(id = 1, vehicleId = 1, date = ms(d), category = ExpenseCategory.INSURANCE, amountCents = 100000))
        val y = Ledger.taxYear(2026, trips, exp, emptyList(), listOf(car.copy(deductionMethod = DeductionMethod.ACTUAL_EXPENSES)), TaxMath.DEFAULT_RATES, 0.12, zone)
        assertEquals(75000L, y.vehicleDeductionCents)
    }

    @Test fun cryptoIncomeUsesFairMarketValueAndFlagsMissing() {
        val d = LocalDate.of(2026, 10, 20)
        val pays = listOf(
            Payment(id = 1, date = ms(d), kind = PaymentKind.BASE, currency = PaymentCurrency.USDC, amountMicros = 50_000_000, usdFmvCents = 5000),
            Payment(id = 2, date = ms(d), kind = PaymentKind.BURST, currency = PaymentCurrency.HONEY, amountMicros = 900_000_000, usdFmvCents = null),
            Payment(id = 3, date = ms(d), kind = PaymentKind.BURST, currency = PaymentCurrency.USDC, amountMicros = 10_000_000, usdFmvCents = 1000, status = PaymentStatus.EXPECTED),
        )
        val y = Ledger.taxYear(2026, emptyList(), emptyList(), pays, listOf(car), TaxMath.DEFAULT_RATES, 0.12, zone)
        assertEquals(5000L, y.incomeCents)
        assertEquals(1, y.incomeMissingFmv)
    }

    @Test fun milesBasisPrefersAdjustmentThenOdometer() {
        val t = trip(1, LocalDate.of(2026, 9, 1), 20.0, TripClass.BUSINESS).copy(startOdometer = 100.0, endOdometer = 121.0)
        assertEquals(21.0 to Ledger.MilesBasis.ODOMETER, Ledger.totalMiles(t))
        assertEquals(19.0 to Ledger.MilesBasis.ADJUSTED, Ledger.totalMiles(t.copy(adjustedMiles = 19.0)))
        assertEquals(Ledger.MilesBasis.GPS, Ledger.totalMiles(t.copy(startOdometer = null)).second)
    }

    @Test fun selfEmploymentTaxEstimate() {
        assertEquals(Math.round(1_000_000 * 0.9235 * 0.153), TaxMath.selfEmploymentTaxCents(1_000_000))
        assertEquals(0L, TaxMath.selfEmploymentTaxCents(-500))
    }

    @Test fun historicalGoalsAreSeparateFromMileage() {
        assertEquals(7, Ledger.HISTORICAL_GOALS.size)
        assertEquals(3625.5, Ledger.HISTORICAL_GOALS.sumOf { it.second }, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 9) to 1712.0, Ledger.HISTORICAL_GOALS[5])
    }

    @Test fun weeklyReportComputesPerHourAndPerMile() {
        val d = LocalDate.of(2026, 11, 3) // Tuesday
        val t = trip(1, d, 50.0, TripClass.BUSINESS) // 4 h trip
        val p = Payment(id = 1, date = ms(d), kind = PaymentKind.BASE, currency = PaymentCurrency.USD, amountMicros = 0, usdFmvCents = 12000)
        val e = Expense(id = 1, vehicleId = 1, date = ms(d), category = ExpenseCategory.GAS, amountCents = 2000)
        val rows = Ledger.report(Ledger.Period.WEEK, listOf(t), listOf(e), listOf(p), zone)
        assertEquals(1, rows.size)
        assertEquals(LocalDate.of(2026, 11, 2), rows[0].start)
        assertEquals(10000L, rows[0].netCents)
        assertEquals(25.0, rows[0].netPerHour!!, 1e-6)
        assertEquals(2.0, rows[0].netPerMile!!, 1e-6)
    }
}
