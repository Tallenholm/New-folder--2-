package com.mapledger.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** How a vehicle's costs are deducted. Prevents double-counting mileage rate + actual costs. */
enum class DeductionMethod { STANDARD_MILEAGE, ACTUAL_EXPENSES, UNDECIDED }

enum class TripStatus { RECORDING, PAUSED, INTERRUPTED, COMPLETED }

/** Tax classification. New trips are always UNCLASSIFIED until the user confirms. */
enum class TripClass { UNCLASSIFIED, BUSINESS, PERSONAL, MIXED }

/** Where a record came from — shown in the IRS log as evidence quality. */
enum class RecordSource { LIVE_GPS, MANUAL, GPX_IMPORT, TIMELINE_IMPORT, CSV_IMPORT }

enum class UploadStatus { UNKNOWN, PENDING, UPLOADED, PROCESSED }

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val year: Int?,
    val make: String,
    val model: String,
    val trim: String = "",
    val isPrimary: Boolean = false,
    val deductionMethod: DeductionMethod = DeductionMethod.UNDECIDED,
    /** First year the vehicle was used for business — standard mileage must be chosen in year one. */
    val firstBusinessYear: Int? = null,
    val isRental: Boolean = false,
    val active: Boolean = true,
    val notes: String = "",
)

@Entity(
    tableName = "trips",
    foreignKeys = [ForeignKey(entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("vehicleId"), Index("startTime"), Index("status"), Index(value = ["importHash"], unique = true)],
)
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val status: TripStatus,
    val source: RecordSource,
    val startTime: Long,
    val endTime: Long? = null,
    val startLat: Double? = null,
    val startLon: Double? = null,
    val endLat: Double? = null,
    val endLon: Double? = null,
    val startLabel: String = "",
    val endLabel: String = "",
    val startOdometer: Double? = null,
    val endOdometer: Double? = null,
    val startOdometerPhoto: String? = null,
    val endOdometerPhoto: String? = null,
    /** Distance supported by accepted GPS fixes. */
    val gpsMeters: Double = 0.0,
    /** Straight-line length of GPS gaps. NOT included in gpsMeters; unverified. */
    val gapMeters: Double = 0.0,
    val gapCount: Int = 0,
    /** Miles the user chose to record (audited). null = use GPS/odometer. */
    val adjustedMiles: Double? = null,
    val movingMs: Long = 0,
    val pausedMs: Long = 0,
    val pausedAt: Long? = null,
    val segmentCount: Int = 1,
    val classification: TripClass = TripClass.UNCLASSIFIED,
    val businessMiles: Double = 0.0,
    val personalMiles: Double = 0.0,
    val purpose: String = "",
    val startsAtHome: Boolean = false,
    val endsAtHome: Boolean = false,
    /** Home-to-work travel may be non-deductible commuting; must be reviewed, never assumed. */
    val needsTaxReview: Boolean = false,
    val confidence: Int = 0,
    val beeUploadStatus: UploadStatus = UploadStatus.UNKNOWN,
    val notes: String = "",
    val importHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["tripId", "time"])],
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val segment: Int,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val altitudeM: Double? = null,
    val speedMps: Float? = null,
    val bearing: Float? = null,
    /** FixFlag bitmask from DistanceEngine. */
    val flags: Int,
)

/** Every manual change to a trip / payment / expense is recorded here. */
@Entity(tableName = "audit_log", indices = [Index(value = ["entityType", "entityId"])])
data class AuditEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: Long,
    val time: Long = System.currentTimeMillis(),
    val field: String,
    val oldValue: String?,
    val newValue: String?,
    val reason: String = "",
)

enum class ExpenseCategory(val label: String, val vehicleOperating: Boolean) {
    GAS("Gas / charging", true),
    REPAIRS("Repairs & maintenance", true),
    TIRES("Tires", true),
    INSURANCE("Insurance", true),
    VEHICLE_RENTAL("Vehicle rental / lease", true),
    REGISTRATION("Registration & fees", true),
    PARKING_TOLLS("Parking & tolls", false),
    MAPPING_EQUIPMENT("Mapping equipment", false),
    PHONE_DATA("Phone & data", false),
    OTHER("Other business", false),
}

@Entity(tableName = "expenses", indices = [Index("date"), Index("vehicleId")])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long?,
    val date: Long,              // epoch millis (local midnight)
    val category: ExpenseCategory,
    val amountCents: Long,
    val vendor: String = "",
    /** Percentage of this expense used for the mapping business (0–100). */
    val businessPct: Int = 100,
    val gallons: Double? = null,
    val odometer: Double? = null,
    val receiptPhoto: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

enum class PaymentKind { BASE, BURST, BONUS, REFERRAL, OTHER }
enum class PaymentCurrency { USD, USDC, HONEY, OTHER }
enum class PaymentStatus { EXPECTED, RECEIVED }

@Entity(tableName = "payments", indices = [Index("date"), Index(value = ["importHash"], unique = true), Index("burstTargetId")])
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val kind: PaymentKind,
    val currency: PaymentCurrency,
    /** Token/cash amount in micro-units (1.5 USDC = 1_500_000). */
    val amountMicros: Long,
    /** USD fair-market value at receipt, in cents. Required for crypto income. */
    val usdFmvCents: Long?,
    val status: PaymentStatus = PaymentStatus.RECEIVED,
    val expectedUsdCents: Long? = null,
    val campaignId: String = "",
    val burstTargetId: Long? = null,
    val txHash: String = "",
    val notes: String = "",
    val importHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "planned_routes")
data class PlannedRoute(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val importedAt: Long = System.currentTimeMillis(),
    val sourceFile: String = "",
    val lengthMeters: Double = 0.0,
    val pointCount: Int = 0,
    val contentHash: String = "",
)

@Entity(
    tableName = "route_points",
    foreignKeys = [ForeignKey(entity = PlannedRoute::class, parentColumns = ["id"], childColumns = ["routeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["routeId", "seq"])],
)
data class RoutePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long,
    val seq: Int,
    val lat: Double,
    val lon: Double,
    /** Set by coverage analysis when a verified GPS fix passed within tolerance. */
    val coveredByTripId: Long? = null,
)

/**
 * PASSED_UNVERIFIED means our GPS evidence shows the vehicle went near the target.
 * It does NOT mean Bee Maps credited it — only a linked payment moves it to CREDITED.
 */
enum class BurstStatus { PLANNED, PASSED_UNVERIFIED, CREDITED, MISSING_PAYMENT, NOT_ELIGIBLE }

@Entity(tableName = "burst_targets", indices = [Index("campaignId")])
data class BurstTarget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Double = 75.0,
    val campaignId: String = "",
    val expectedRewardCents: Long? = null,
    val status: BurstStatus = BurstStatus.PLANNED,
    val firstPassedTripId: Long? = null,
    val firstPassedTime: Long? = null,
    val passCount: Int = 0,
    val notes: String = "",
)

/** Recorded Bee Maps weekly road-goal distances. NOT odometer readings, NOT deductible miles. */
@Entity(tableName = "historical_goals", indices = [Index(value = ["weekStart"], unique = true)])
data class HistoricalGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStart: Long,       // epoch day
    val distanceKm: Double,
    val source: String = "Bee Maps goal (manual entry)",
    val evidencePhoto: String? = null,
    val notes: String = "",
)

@Entity(tableName = "mileage_rates")
data class MileageRate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val tenthsOfCent: Int,
    val label: String,
)

/** One row per imported file, keyed by content hash to block re-importing the same data. */
@Entity(tableName = "import_records", indices = [Index(value = ["contentHash"], unique = true)])
data class ImportRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val fileName: String,
    val contentHash: String,
    val importedAt: Long = System.currentTimeMillis(),
    val rowsImported: Int,
    val rowsSkipped: Int,
)
