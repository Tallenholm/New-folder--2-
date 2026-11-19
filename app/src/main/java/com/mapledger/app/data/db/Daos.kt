package com.mapledger.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY isPrimary DESC, name") fun observeAll(): Flow<List<Vehicle>>
    @Query("SELECT * FROM vehicles ORDER BY isPrimary DESC, name") suspend fun all(): List<Vehicle>
    @Query("SELECT * FROM vehicles WHERE id = :id") suspend fun get(id: Long): Vehicle?
    @Query("SELECT * FROM vehicles WHERE isPrimary = 1 LIMIT 1") suspend fun primary(): Vehicle?
    @Insert suspend fun insert(v: Vehicle): Long
    @Update suspend fun update(v: Vehicle)
    @Query("DELETE FROM vehicles") suspend fun deleteAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(v: List<Vehicle>)
}

data class TripTotals(val trips: Int, val gpsMeters: Double?, val businessMiles: Double?, val personalMiles: Double?)

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startTime DESC") fun observeAll(): Flow<List<Trip>>
    @Query("SELECT * FROM trips ORDER BY startTime") suspend fun all(): List<Trip>
    @Query("SELECT * FROM trips WHERE id = :id") fun observe(id: Long): Flow<Trip?>
    @Query("SELECT * FROM trips WHERE id = :id") suspend fun get(id: Long): Trip?
    @Query("SELECT * FROM trips WHERE status IN ('RECORDING','PAUSED','INTERRUPTED') ORDER BY startTime DESC LIMIT 1")
    suspend fun active(): Trip?
    @Query("SELECT * FROM trips WHERE status IN ('RECORDING','PAUSED','INTERRUPTED') ORDER BY startTime DESC LIMIT 1")
    fun observeActive(): Flow<Trip?>
    @Query("SELECT * FROM trips WHERE startTime >= :from AND startTime < :to AND status = 'COMPLETED' ORDER BY startTime")
    suspend fun completedBetween(from: Long, to: Long): List<Trip>
    @Query("SELECT * FROM trips WHERE startTime >= :from AND startTime < :to AND status = 'COMPLETED' ORDER BY startTime")
    fun observeCompletedBetween(from: Long, to: Long): Flow<List<Trip>>
    @Query("SELECT COUNT(*) FROM trips WHERE importHash = :hash") suspend fun countByHash(hash: String): Int
    @Insert suspend fun insert(t: Trip): Long
    @Update suspend fun update(t: Trip)
    @Query("DELETE FROM trips WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM trips") suspend fun deleteAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(t: List<Trip>)
}

@Dao
interface TrackPointDao {
    @Insert suspend fun insert(p: TrackPoint): Long
    @Insert suspend fun insertAll(p: List<TrackPoint>)
    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY time, id") suspend fun forTrip(tripId: Long): List<TrackPoint>
    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY time, id") fun observeForTrip(tripId: Long): Flow<List<TrackPoint>>
    @Query("SELECT * FROM track_points WHERE flags NOT IN (1,2,4) AND lat BETWEEN :minLat AND :maxLat AND lon BETWEEN :minLon AND :maxLon")
    suspend fun usableInBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<TrackPoint>
    @Query("SELECT COUNT(*) FROM track_points WHERE tripId = :tripId") suspend fun count(tripId: Long): Int
    @Query("SELECT * FROM track_points ORDER BY tripId, time") suspend fun all(): List<TrackPoint>
    @Query("DELETE FROM track_points") suspend fun deleteAll()
}

@Dao
interface AuditDao {
    @Insert suspend fun insert(a: AuditEntry)
    @Query("SELECT * FROM audit_log WHERE entityType = :type AND entityId = :id ORDER BY time DESC")
    fun observeFor(type: String, id: Long): Flow<List<AuditEntry>>
    @Query("SELECT * FROM audit_log ORDER BY time") suspend fun all(): List<AuditEntry>
    @Query("DELETE FROM audit_log") suspend fun deleteAll()
    @Insert suspend fun insertAll(a: List<AuditEntry>)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC") fun observeAll(): Flow<List<Expense>>
    @Query("SELECT * FROM expenses ORDER BY date") suspend fun all(): List<Expense>
    @Query("SELECT * FROM expenses WHERE id = :id") suspend fun get(id: Long): Expense?
    @Insert suspend fun insert(e: Expense): Long
    @Update suspend fun update(e: Expense)
    @Query("DELETE FROM expenses WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM expenses") suspend fun deleteAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(e: List<Expense>)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY date DESC") fun observeAll(): Flow<List<Payment>>
    @Query("SELECT * FROM payments ORDER BY date") suspend fun all(): List<Payment>
    @Query("SELECT * FROM payments WHERE id = :id") suspend fun get(id: Long): Payment?
    @Query("SELECT COUNT(*) FROM payments WHERE importHash = :hash") suspend fun countByHash(hash: String): Int
    @Insert suspend fun insert(p: Payment): Long
    @Update suspend fun update(p: Payment)
    @Query("DELETE FROM payments WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM payments") suspend fun deleteAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(p: List<Payment>)
}

@Dao
interface BeeDao {
    @Query("SELECT * FROM planned_routes ORDER BY importedAt DESC") fun observeRoutes(): Flow<List<PlannedRoute>>
    @Query("SELECT * FROM planned_routes") suspend fun routes(): List<PlannedRoute>
    @Query("SELECT COUNT(*) FROM planned_routes WHERE contentHash = :hash") suspend fun countRouteHash(hash: String): Int
    @Insert suspend fun insertRoute(r: PlannedRoute): Long
    @Query("DELETE FROM planned_routes WHERE id = :id") suspend fun deleteRoute(id: Long)
    @Insert suspend fun insertRoutePoints(p: List<RoutePoint>)
    @Query("SELECT * FROM route_points WHERE routeId = :routeId ORDER BY seq") suspend fun routePoints(routeId: Long): List<RoutePoint>
    @Query("SELECT * FROM route_points ORDER BY routeId, seq") suspend fun allRoutePoints(): List<RoutePoint>
    @Query("UPDATE route_points SET coveredByTripId = :tripId WHERE id IN (:ids) AND coveredByTripId IS NULL")
    suspend fun markCovered(ids: List<Long>, tripId: Long)
    @Query("SELECT routeId, COUNT(*) AS total, SUM(CASE WHEN coveredByTripId IS NOT NULL THEN 1 ELSE 0 END) AS covered FROM route_points GROUP BY routeId")
    fun observeCoverage(): Flow<List<RouteCoverage>>

    @Query("SELECT * FROM burst_targets ORDER BY status, name") fun observeTargets(): Flow<List<BurstTarget>>
    @Query("SELECT * FROM burst_targets") suspend fun targets(): List<BurstTarget>
    @Query("SELECT * FROM burst_targets WHERE id = :id") suspend fun target(id: Long): BurstTarget?
    @Insert suspend fun insertTarget(t: BurstTarget): Long
    @Insert suspend fun insertTargets(t: List<BurstTarget>)
    @Update suspend fun updateTarget(t: BurstTarget)
    @Query("DELETE FROM burst_targets WHERE id = :id") suspend fun deleteTarget(id: Long)

    @Query("SELECT * FROM historical_goals ORDER BY weekStart") fun observeGoals(): Flow<List<HistoricalGoal>>
    @Query("SELECT * FROM historical_goals ORDER BY weekStart") suspend fun goals(): List<HistoricalGoal>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertGoals(g: List<HistoricalGoal>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertGoal(g: HistoricalGoal)

    @Query("DELETE FROM planned_routes") suspend fun deleteAllRoutes()
    @Query("DELETE FROM burst_targets") suspend fun deleteAllTargets()
    @Query("DELETE FROM historical_goals") suspend fun deleteAllGoals()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRoutesRaw(r: List<PlannedRoute>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTargetsRaw(t: List<BurstTarget>)
}

data class RouteCoverage(val routeId: Long, val total: Int, val covered: Int)

@Dao
interface RateDao {
    @Query("SELECT * FROM mileage_rates ORDER BY fromEpochDay") fun observeAll(): Flow<List<MileageRate>>
    @Query("SELECT * FROM mileage_rates ORDER BY fromEpochDay") suspend fun all(): List<MileageRate>
    @Insert suspend fun insert(r: MileageRate): Long
    @Insert suspend fun insertAll(r: List<MileageRate>)
    @Query("DELETE FROM mileage_rates WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM mileage_rates") suspend fun deleteAll()
}

@Dao
interface ImportDao {
    @Query("SELECT COUNT(*) FROM import_records WHERE contentHash = :hash") suspend fun countByHash(hash: String): Int
    @Insert suspend fun insert(r: ImportRecord): Long
    @Query("SELECT * FROM import_records ORDER BY importedAt DESC") fun observeAll(): Flow<List<ImportRecord>>
}
