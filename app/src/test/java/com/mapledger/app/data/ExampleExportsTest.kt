package com.mapledger.app.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapledger.app.data.db.*
import com.mapledger.app.data.repo.*
import com.mapledger.app.domain.DistanceEngine
import com.mapledger.app.domain.DriveSim
import com.mapledger.app.domain.GeoMath
import com.mapledger.app.domain.TaxMath
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Builds a realistic sample ledger (synthetic data, Manteno IL home base) and runs the real export
 * code. Output goes to app/build/examples and is copied into docs/examples for reference.
 */
@RunWith(RobolectricTestRunner::class)
class ExampleExportsTest {
    @Test fun generateExamples() = runBlocking {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Chicago"))
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsRepository(ctx)
        settings.setHome(DriveSim.START_LAT, DriveSim.START_LON, "Home (Manteno, IL)", 150.0, false)
        val trips = TripRepository(db, settings); val finance = FinanceRepository(db); val bee = BeeRepository(db, settings)
        val transfer = TransferRepository(ctx, db, finance, bee, settings)
        db.rates().insertAll(TaxMath.DEFAULT_RATES.map { MileageRate(fromEpochDay = it.from.toEpochDay(), toEpochDay = it.to.toEpochDay(), tenthsOfCent = it.tenthsOfCent, label = it.label) })
        val car = finance.saveVehicle(Vehicle(name = "Elantra Hybrid", year = 2025, make = "Hyundai", model = "Elantra Hybrid", trim = "Limited", isPrimary = true,
            deductionMethod = DeductionMethod.STANDARD_MILEAGE, firstBusinessYear = 2026))
        val z = ZoneId.of("America/Chicago")
        fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(z).toInstant().toEpochMilli()

        // Three live-GPS sessions (synthetic routes) starting and ending at home.
        val days = listOf(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 9))
        days.forEachIndexed { i, d ->
            val t = trips.startTrip(car, 12_000.0 + i * 100, null)
            db.trips().update(db.trips().get(t.id)!!.copy(startTime = at(d, 9)))
            val legMi = 10.0 + i * 4
            val fixes = DriveSim.drive(listOf(Triple(200.0, GeoMath.milesToMeters(legMi), 24.0), Triple(110.0, GeoMath.milesToMeters(3.0), 13.0),
                Triple(20.0, GeoMath.milesToMeters(legMi), 24.0), Triple(290.0, GeoMath.milesToMeters(3.0), 13.0)), startTime = at(d, 9), noiseM = 2.5, seed = i)
            val e = DistanceEngine(trips.engineConfig())
            fixes.forEach { f -> trips.appendFix(t.id, f, e.offer(f), null, null, e.snapshot()) }
            val miles = GeoMath.metersToMiles(e.snapshot().verifiedMeters)
            trips.finalizeTrip(t.id, 12_000.0 + i * 100 + Math.round(miles * 10) / 10.0, null) { la, lo -> "%.4f, %.4f".format(la, lo) }
            trips.classify(t.id, TripClass.BUSINESS, "Bee Maps mapping session – Kankakee County road goal", null, null)
            if (i < 2) trips.setReviewed(t.id, true, "Mapping work itself is the business activity; trip starts mapping at driveway (confirm with preparer)")
        }
        trips.addManualTrip(car, at(LocalDate.of(2026, 11, 6), 17), at(LocalDate.of(2026, 11, 6), 18), 6.2, null, null, "Home", "Grocery", "", TripClass.PERSONAL)

        finance.saveExpense(Expense(vehicleId = car, date = at(days[0], 8), category = ExpenseCategory.GAS, amountCents = 3412, vendor = "Shell Manteno", gallons = 10.8))
        finance.saveExpense(Expense(vehicleId = car, date = at(days[1], 8), category = ExpenseCategory.PARKING_TOLLS, amountCents = 285, vendor = "I-Pass"))
        finance.saveExpense(Expense(vehicleId = null, date = at(days[1], 8), category = ExpenseCategory.PHONE_DATA, amountCents = 6500, vendor = "Carrier", businessPct = 40))
        finance.saveExpense(Expense(vehicleId = null, date = at(days[2], 8), category = ExpenseCategory.MAPPING_EQUIPMENT, amountCents = 2999, vendor = "Dash mount"))
        finance.savePayment(Payment(date = at(days[1], 12), kind = PaymentKind.BASE, currency = PaymentCurrency.USDC, amountMicros = 38_250_000, usdFmvCents = 3825, txHash = "SAMPLE-1"))
        finance.savePayment(Payment(date = at(days[2], 12), kind = PaymentKind.BURST, currency = PaymentCurrency.USDC, amountMicros = 12_000_000, usdFmvCents = 1200, campaignId = "SAMPLE-CAMPAIGN", txHash = "SAMPLE-2"))
        finance.savePayment(Payment(date = at(days[2], 13), kind = PaymentKind.BONUS, currency = PaymentCurrency.HONEY, amountMicros = 450_000_000, usdFmvCents = null, txHash = "SAMPLE-3"))

        val out = File("build/examples").apply { mkdirs() }
        fun u(n: String) = Uri.fromFile(File(out, n))
        transfer.exportIrsCsv(u("MapLedger-2026-mileage-log.csv"), 2026)
        transfer.exportXlsx(u("MapLedger-2026.xlsx"), 2026)
        // android.graphics.pdf.PdfDocument has no native implementation under Robolectric, so the PDF
        // export can only be exercised on a device/emulator.
        runCatching { transfer.exportPdf(u("MapLedger-2026-mileage-log.pdf"), 2026) }
        transfer.exportTripsGpx(u("MapLedger-2026-tracks.gpx"), db.trips().all().filter { it.source == RecordSource.LIVE_GPS }.map { it.id })
        transfer.exportJson(u("MapLedger-data.json"))
        listOf("csv", "xlsx", "gpx", "json").forEach { ext -> assertTrue(out.listFiles()!!.any { it.name.endsWith(ext) && it.length() > 100 }) }
        db.close()
    }
}
