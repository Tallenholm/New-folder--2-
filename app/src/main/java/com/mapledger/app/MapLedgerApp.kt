package com.mapledger.app

import android.app.Application
import androidx.work.Configuration
import com.mapledger.app.data.db.AppDatabase
import com.mapledger.app.data.db.HistoricalGoal
import com.mapledger.app.data.db.MileageRate
import com.mapledger.app.data.db.Vehicle
import com.mapledger.app.data.db.DeductionMethod
import com.mapledger.app.data.repo.BeeRepository
import com.mapledger.app.data.repo.FinanceRepository
import com.mapledger.app.data.repo.SettingsRepository
import com.mapledger.app.data.repo.TransferRepository
import com.mapledger.app.data.repo.TripRepository
import com.mapledger.app.domain.Ledger
import com.mapledger.app.domain.TaxMath
import com.mapledger.app.tracking.TrackingService
import com.mapledger.app.tracking.WatchdogWorker
import com.mapledger.app.util.PlaceLabeler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Manual dependency container (keeps the build simple; no DI framework needed). */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.build(app)
    val settings = SettingsRepository(app)
    val trips = TripRepository(db, settings)
    val finance = FinanceRepository(db)
    val bee = BeeRepository(db, settings)
    val transfer = TransferRepository(app, db, finance, bee, settings)
    val geocoder = PlaceLabeler(app)

    private val seedLock = Mutex()

    /** First-run data: primary vehicle, Manteno home base, IRS rates, historical Bee Maps goals. Idempotent. */
    suspend fun seedIfNeeded() = seedLock.withLock {
        if (settings.current().seeded && db.vehicles().all().isNotEmpty()) return@withLock
        if (db.vehicles().all().isEmpty()) {
            val id = db.vehicles().insert(Vehicle(name = "Elantra Hybrid", year = 2025, make = "Hyundai", model = "Elantra Hybrid", trim = "Limited",
                isPrimary = true, deductionMethod = DeductionMethod.UNDECIDED))
            settings.setActiveVehicle(id)
        }
        if (settings.current().homeLat == null)
            settings.setHome(Ledger.MANTENO_LAT, Ledger.MANTENO_LON, "Home (Manteno, IL)", 2_500.0, approximate = true)
        if (db.rates().all().isEmpty()) db.rates().insertAll(TaxMath.DEFAULT_RATES.map {
            MileageRate(fromEpochDay = it.from.toEpochDay(), toEpochDay = it.to.toEpochDay(), tenthsOfCent = it.tenthsOfCent, label = it.label)
        })
        db.bee().insertGoals(Ledger.HISTORICAL_GOALS.map { (d, km) ->
            HistoricalGoal(weekStart = d.toEpochDay(), distanceKm = km, source = "Bee Maps road goal (historical entry)",
                notes = "Recorded road-goal distance — not an odometer reading and not necessarily deductible miles")
        })
        settings.markSeeded()
    }
}

class MapLedgerApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        TrackingService.createChannels(this)
        container.appScope.launch { container.seedIfNeeded() }
        WatchdogWorker.schedule(this)
    }
}
