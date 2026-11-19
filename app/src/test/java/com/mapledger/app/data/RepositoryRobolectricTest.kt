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
import com.mapledger.app.domain.Ledger
import com.mapledger.app.domain.RawFix
import com.mapledger.app.io.Gpx
import com.mapledger.app.io.GpxPoint
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * End-to-end persistence tests on a real (Robolectric) SQLite database:
 * a full home → mapping → home drive, crash recovery, and duplicate import protection.
 */
@RunWith(RobolectricTestRunner::class)
class RepositoryRobolectricTest {
    private lateinit var ctx: Context
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var trips: TripRepository
    private lateinit var finance: FinanceRepository
    private lateinit var bee: BeeRepository
    private lateinit var transfer: TransferRepository
    private var vehicleId = 0L

    @Before fun setUp() = runBlocking {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(ctx)
        settings.setHome(DriveSim.START_LAT, DriveSim.START_LON, "Home", 150.0, false)
        trips = TripRepository(db, settings); finance = FinanceRepository(db); bee = BeeRepository(db, settings)
        transfer = TransferRepository(ctx, db, finance, bee, settings)
        vehicleId = db.vehicles().insert(Vehicle(name = "Elantra", year = 2025, make = "Hyundai", model = "Elantra Hybrid", isPrimary = true))
    }
    @After fun tearDown() = db.close()

    private suspend fun record(tripId: Long, fixes: List<RawFix>, engine: DistanceEngine) =
        fixes.forEach { f -> val flag = engine.offer(f); trips.appendFix(tripId, f, flag, null, null, engine.snapshot()) }

    private val loop = listOf(Triple(0.0, GeoMath.milesToMeters(8.0), 24.0), Triple(90.0, GeoMath.milesToMeters(2.0), 15.0),
        Triple(180.0, GeoMath.milesToMeters(8.0), 24.0), Triple(270.0, GeoMath.milesToMeters(2.0), 15.0))

    @Test fun homeToHomeDriveIsSavedAndShownInHistory() = runBlocking {
        val trip = trips.startTrip(vehicleId, 12_000.0, null)
        val fixes = DriveSim.drive(loop, noiseM = 2.0)
        record(trip.id, fixes, DistanceEngine(trips.engineConfig()))

        // Every fix is already persisted before the trip is finished.
        assertEquals(fixes.size, db.points().count(trip.id))
        assertEquals(20.0, GeoMath.metersToMiles(db.trips().get(trip.id)!!.gpsMeters), 0.2)

        val done = trips.finalizeTrip(trip.id, 12_020.2, null) { _, _ -> "Somewhere" }!!
        assertEquals(TripStatus.COMPLETED, done.status)
        assertTrue(done.startsAtHome); assertTrue(done.endsAtHome)
        assertTrue("home travel flagged for review", done.needsTaxReview)
        assertEquals(TripClass.UNCLASSIFIED, done.classification)
        assertEquals("Home", done.startLabel)
        assertEquals(20.2 to Ledger.MilesBasis.ODOMETER, Ledger.totalMiles(done).let { (Math.round(it.first * 10) / 10.0) to it.second })

        val history = db.trips().all()
        assertEquals(1, history.size)
        assertEquals(trip.id, history.single().id)

        trips.classify(trip.id, TripClass.BUSINESS, "Bee Maps mapping – Kankakee County", null, null)
        val audit = db.audit().all().filter { it.entityId == trip.id }
        assertTrue(audit.any { it.field == "classification" && it.newValue == "BUSINESS" })
    }

    @Test fun recordingSurvivesProcessDeath() = runBlocking {
        val trip = trips.startTrip(vehicleId, null, null)
        val fixes = DriveSim.drive(listOf(Triple(0.0, 10_000.0, 22.0)))
        val cut = fixes.size / 2
        record(trip.id, fixes.take(cut), DistanceEngine(trips.engineConfig()))
        val before = db.trips().get(trip.id)!!.gpsMeters

        // Process dies: in-memory engine is gone. A new repository + engine is rebuilt from Room.
        val trips2 = TripRepository(db, settings)
        val active = trips2.active()!!
        assertEquals(trip.id, active.id)
        val (engine, _) = trips2.rebuildEngine(active.id)
        assertEquals(before, engine.snapshot().verifiedMeters, 1e-6)
        fixes.drop(cut).forEach { f -> val flag = engine.offer(f); trips2.appendFix(active.id, f, flag, null, null, engine.snapshot()) }
        val done = trips2.finalizeTrip(active.id, null, null) { _, _ -> "x" }!!
        assertEquals(10_000.0, done.gpsMeters, 40.0)
        assertEquals(fixes.size, db.points().count(trip.id))
    }

    @Test fun pauseResumeTracksPausedTime() = runBlocking {
        val trip = trips.startTrip(vehicleId, null, null)
        trips.setStatus(trip.id, TripStatus.PAUSED)
        Thread.sleep(20)
        trips.setStatus(trip.id, TripStatus.RECORDING)
        val t = db.trips().get(trip.id)!!
        assertTrue(t.pausedMs >= 15); assertEquals(2, t.segmentCount); assertNull(t.pausedAt)
    }

    private fun file(name: String, body: String): Uri = Uri.fromFile(File(ctx.cacheDir, name).apply { writeText(body) })

    @Test fun duplicateGpxTripImportIsBlocked() = runBlocking {
        val fixes = DriveSim.drive(listOf(Triple(45.0, 6_000.0, 20.0)))
        val sw = java.io.StringWriter()
        Gpx.write(sw, listOf(Gpx.ExportTrack("Old drive", listOf(fixes.map { GpxPoint(it.lat, it.lon, it.time) }))))
        val uri = file("drive.gpx", sw.toString())
        val first = transfer.importGpx(uri, TransferRepository.GpxMode.DRIVEN_TRIP, vehicleId)
        assertEquals(1, first.imported)
        val second = transfer.importGpx(uri, TransferRepository.GpxMode.DRIVEN_TRIP, vehicleId)
        assertEquals(0, second.imported)
        // Same track in a different file (e.g. re-exported) is caught by the per-track hash.
        val uri2 = file("drive-copy.gpx", sw.toString() + "\n<!-- copy -->")
        val third = transfer.importGpx(uri2, TransferRepository.GpxMode.DRIVEN_TRIP, vehicleId)
        assertEquals(0, third.imported); assertEquals(1, third.skipped)
        val t = db.trips().all().single()
        assertEquals(RecordSource.GPX_IMPORT, t.source)
        assertEquals(6_000.0, t.gpsMeters, 30.0)
    }

    @Test fun duplicatePaymentImportIsBlocked() = runBlocking {
        val csv = "date,amount,currency,type,tx\n2026-10-05,10,USDC,burst,abc\n2026-10-06,25,USDC,base,def\n"
        assertEquals(2, transfer.importPayments(file("p1.csv", csv)).imported)
        assertEquals(0, transfer.importPayments(file("p1.csv", csv)).imported)
        // Overlapping export with one new row → only the new row is added.
        val r = transfer.importPayments(file("p2.csv", csv + "2026-10-07,5,USDC,burst,ghi\n"))
        assertEquals(1, r.imported); assertEquals(2, r.skipped)
        assertEquals(3, db.payments().all().size)
    }

    @Test fun burstPassIsEvidenceNotCredit() = runBlocking {
        val fixes = DriveSim.drive(listOf(Triple(90.0, 5_000.0, 20.0)))
        val mid = fixes[fixes.size / 2]
        val targetId = bee.saveTarget(BurstTarget(name = "Burst A", lat = mid.lat, lon = mid.lon, expectedRewardCents = 500))
        val trip = trips.startTrip(vehicleId, null, null)
        record(trip.id, fixes, DistanceEngine())
        trips.finalizeTrip(trip.id, null, null) { _, _ -> "x" }
        bee.analyzeTrip(trip.id); bee.reconcile(now = fixes.last().time + 1000)
        assertEquals(BurstStatus.PASSED_UNVERIFIED, db.bee().target(targetId)!!.status)
        bee.reconcile(now = fixes.last().time + 30L * 86_400_000)
        assertEquals(BurstStatus.MISSING_PAYMENT, db.bee().target(targetId)!!.status)
        finance.savePayment(Payment(date = fixes.last().time, kind = PaymentKind.BURST, currency = PaymentCurrency.USDC, amountMicros = 5_000_000, usdFmvCents = 500, burstTargetId = targetId))
        bee.reconcile()
        assertEquals(BurstStatus.CREDITED, db.bee().target(targetId)!!.status)
    }

    @Test fun encryptedBackupRestoresEverything() = runBlocking {
        val trip = trips.startTrip(vehicleId, null, null)
        record(trip.id, DriveSim.drive(listOf(Triple(0.0, 2_000.0, 20.0))), DistanceEngine())
        trips.finalizeTrip(trip.id, null, null) { _, _ -> "x" }
        finance.saveExpense(Expense(vehicleId = vehicleId, date = 1, category = ExpenseCategory.GAS, amountCents = 4200))
        val out = Uri.fromFile(File(ctx.cacheDir, "b.mlbackup"))
        transfer.backup(out, "passphrase1".toCharArray())
        val points = db.points().count(trip.id)
        db.trips().deleteAll(); db.expenses().deleteAll()
        assertEquals(1, transfer.restore(out, "passphrase1".toCharArray()))
        assertEquals(points, db.points().count(trip.id))
        assertEquals(4200L, db.expenses().all().single().amountCents)
    }
}
