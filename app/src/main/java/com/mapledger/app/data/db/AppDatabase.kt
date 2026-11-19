package com.mapledger.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

@Database(
    entities = [
        Vehicle::class, Trip::class, TrackPoint::class, AuditEntry::class, Expense::class, Payment::class,
        PlannedRoute::class, RoutePoint::class, BurstTarget::class, HistoricalGoal::class, MileageRate::class,
        ImportRecord::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicles(): VehicleDao
    abstract fun trips(): TripDao
    abstract fun points(): TrackPointDao
    abstract fun audit(): AuditDao
    abstract fun expenses(): ExpenseDao
    abstract fun payments(): PaymentDao
    abstract fun bee(): BeeDao
    abstract fun rates(): RateDao
    abstract fun imports(): ImportDao

    companion object {
        const val VERSION = 1
        const val NAME = "mapledger.db"

        /**
         * Explicit migrations only. Destructive fallback is intentionally NOT enabled: a schema
         * mistake must crash loudly in testing rather than silently wiping a tax log.
         * Add `Migration(n, n+1)` objects here and bump [VERSION]; schemas are exported to app/schemas.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
