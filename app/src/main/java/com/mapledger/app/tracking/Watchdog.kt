package com.mapledger.app.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mapledger.app.MapLedgerApp
import com.mapledger.app.data.db.TripStatus
import java.util.concurrent.TimeUnit

/**
 * Periodic safety net. Android may kill the process (OEM battery savers on some Samsung models are
 * aggressive). If a trip is still marked RECORDING but no service is alive in this process, mark it
 * INTERRUPTED and notify the user. We do not restart location tracking from the background — that
 * would require background location permission and violate the "user-started" model.
 * Also runs Bee Maps payment reconciliation.
 */
class WatchdogWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as MapLedgerApp).container
        val t = c.trips.active()
        if (t != null && t.status == TripStatus.RECORDING && !TrackingState.state.value.running) {
            c.trips.setStatus(t.id, TripStatus.INTERRUPTED, "Watchdog: recording service was not running")
            TrackingService.postInterruptedNotification(applicationContext)
        }
        c.bee.reconcile()
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("watchdog", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build())
        }
    }
}
