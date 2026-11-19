package com.mapledger.app.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapledger.app.MainActivity
import com.mapledger.app.MapLedgerApp
import com.mapledger.app.R
import com.mapledger.app.data.db.TripStatus
import com.mapledger.app.domain.DistanceEngine
import com.mapledger.app.domain.FixFlag
import com.mapledger.app.domain.GeoMath
import com.mapledger.app.domain.LatLon
import com.mapledger.app.domain.RawFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Foreground location service (type=location). Started only from a visible UI action, so it holds
 * "while-in-use" location access even when the screen is locked or another app (Bee Maps, Google Maps)
 * is in front. Every fix is written to Room before the UI is updated, so a crash loses at most the
 * fix that was in flight.
 */
class TrackingService : LifecycleService() {

    private val app get() = application as MapLedgerApp
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val mutex = Mutex()
    private var engine: DistanceEngine? = null
    private var tripId: Long? = null
    private var segment = 0
    private var updatesOn = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val locs = result.locations.toList()
            lifecycleScope.launch { locs.forEach { onFix(it) } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Must enter the foreground promptly on every start.
        if (!goForeground()) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_START -> lifecycleScope.launch {
                start(intent.getLongExtra(EXTRA_VEHICLE, -1), intent.getDoubleExtra(EXTRA_ODO, Double.NaN).takeIf { !it.isNaN() }, intent.getStringExtra(EXTRA_PHOTO))
            }
            ACTION_PAUSE -> lifecycleScope.launch { pause() }
            ACTION_RESUME -> lifecycleScope.launch { resume() }
            ACTION_STOP -> lifecycleScope.launch { stop(intent.getDoubleExtra(EXTRA_ODO, Double.NaN).takeIf { !it.isNaN() }, intent.getStringExtra(EXTRA_PHOTO)) }
            ACTION_RECOVER, null -> lifecycleScope.launch { recover(sticky = intent == null) }
        }
        return START_STICKY
    }

    private fun goForeground(): Boolean = try {
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(TrackingState.state.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        true
    } catch (e: Exception) {
        // Android refused (e.g. system restart while app is in background without location access).
        // Never lose the trip: mark it interrupted and ask the user to reopen the app.
        lifecycleScope.launch {
            app.container.trips.active()?.let { app.container.trips.setStatus(it.id, TripStatus.INTERRUPTED, "Android blocked restart: ${e.javaClass.simpleName}") }
            postInterruptedNotification(this@TrackingService)
        }
        stopSelf()
        false
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private suspend fun start(vehicleId: Long, odo: Double?, photo: String?) = mutex.withLock {
        val repo = app.container.trips
        if (repo.active() != null) { recoverLocked(false); return@withLock }
        val vid = if (vehicleId > 0) vehicleId else app.container.finance.primaryVehicle()?.id ?: run { fail("Add a vehicle first"); return@withLock }
        val trip = repo.startTrip(vid, odo, photo)
        tripId = trip.id; segment = 0
        engine = DistanceEngine(repo.engineConfig())
        TrackingState.update { LiveTracking(running = true, tripId = trip.id, startTime = trip.startTime) }
        requestUpdates()
        refreshNotification()
    }

    private suspend fun recover(sticky: Boolean) = mutex.withLock { recoverLocked(sticky) }

    /** Rebuild state from Room after a process restart or an "interrupted" trip resume. */
    private suspend fun recoverLocked(sticky: Boolean) {
        val repo = app.container.trips
        val t = repo.active() ?: run { if (tripId == null) { stopSelfClean() }; return }
        if (tripId == t.id && engine != null) return // already running
        val (eng, seg) = repo.rebuildEngine(t.id)
        engine = eng; tripId = t.id
        // Same segment on recovery: the engine's gap rule will record the downtime as an unverified gap.
        segment = seg
        val paused = t.status == TripStatus.PAUSED
        if (t.status == TripStatus.INTERRUPTED) repo.setStatus(t.id, TripStatus.RECORDING, if (sticky) "Auto-restarted by Android" else "Resumed by user after interruption")
        val snap = eng.snapshot()
        val pts = repo.points(t.id)
        TrackingState.update {
            LiveTracking(running = true, paused = paused, tripId = t.id, startTime = t.startTime, pausedMs = t.pausedMs, pausedAt = t.pausedAt,
                verifiedMeters = snap.verifiedMeters, gapMeters = snap.gapStraightLineMeters, acceptedFixes = snap.acceptedFixes,
                rejectedFixes = snap.rejectedFixes, path = buildPath(pts.map { Triple(LatLon(it.lat, it.lon), it.segment, it.flags) }))
        }
        if (!paused) requestUpdates()
        refreshNotification()
    }

    private suspend fun pause() = mutex.withLock {
        val id = tripId ?: return@withLock
        removeUpdates()
        app.container.trips.setStatus(id, TripStatus.PAUSED)
        TrackingState.update { it.copy(paused = true, pausedAt = System.currentTimeMillis(), speedMps = null) }
        refreshNotification()
    }

    private suspend fun resume() = mutex.withLock {
        val id = tripId ?: run { recoverLocked(false); return@withLock }
        app.container.trips.setStatus(id, TripStatus.RECORDING)
        segment += 1 // never join across a pause
        val t = app.container.trips.get(id)
        TrackingState.update { it.copy(paused = false, pausedAt = null, pausedMs = t?.pausedMs ?: it.pausedMs, path = it.path + listOf(emptyList())) }
        requestUpdates()
        refreshNotification()
    }

    private suspend fun stop(odo: Double?, photo: String?) {
        val id = mutex.withLock {
            removeUpdates()
            val id = tripId ?: app.container.trips.active()?.id
            tripId = null; engine = null
            id
        }
        if (id != null) {
            // Finalize on the application scope so it completes even as the service shuts down.
            app.container.appScope.launch {
                val done = app.container.trips.finalizeTrip(id, odo, photo) { lat, lon -> app.container.geocoder.label(lat, lon) }
                if (done != null) { app.container.bee.analyzeTrip(id); app.container.bee.reconcile() }
                TrackingState.update { LiveTracking(error = null) }
            }
        }
        TrackingState.update { it.copy(running = false, paused = false) }
        stopSelfClean()
    }

    private fun stopSelfClean() {
        removeUpdates()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(msg: String) {
        TrackingState.update { it.copy(running = false, error = msg) }
        stopSelfClean()
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        if (updatesOn) return
        if (!hasLocationPermission()) { fail("Location permission is required"); return }
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .setMaxUpdateDelayMillis(0)
            .build()
        fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        updatesOn = true
        acquireWakeLock()
    }

    private fun removeUpdates() {
        if (updatesOn) fused.removeLocationUpdates(callback)
        updatesOn = false
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }

    /** Partial wake lock keeps the CPU processing fixes while the screen is off; released on pause/stop. */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MapLedger:tracking").apply { setReferenceCounted(false); acquire(12 * 60 * 60 * 1000L) }
    }

    private suspend fun onFix(loc: Location) = mutex.withLock {
        val id = tripId ?: return@withLock
        val eng = engine ?: return@withLock
        if (TrackingState.state.value.paused) return@withLock
        val fix = RawFix(loc.time, loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else -1f,
            if (loc.hasSpeed()) loc.speed else null, segment)
        val flag = eng.offer(fix)
        val snap = eng.snapshot()
        withContext(Dispatchers.IO) {
            app.container.trips.appendFix(id, fix, flag, if (loc.hasAltitude()) loc.altitude else null, if (loc.hasBearing()) loc.bearing else null, snap)
        }
        val usable = flag != FixFlag.LOW_ACCURACY && flag != FixFlag.DUPLICATE && flag != FixFlag.IMPOSSIBLE_SPEED
        TrackingState.update { s ->
            var path = s.path.ifEmpty { listOf(emptyList()) }
            if (usable) {
                if (flag == FixFlag.GAP_BEFORE) path = path + listOf(emptyList())
                path = path.dropLast(1) + listOf(path.last() + LatLon(fix.lat, fix.lon))
            }
            s.copy(verifiedMeters = snap.verifiedMeters, gapMeters = snap.gapStraightLineMeters, speedMps = fix.speedMps,
                accuracyM = fix.accuracyM, lastFixTime = fix.time, acceptedFixes = snap.acceptedFixes, rejectedFixes = snap.rejectedFixes, path = path)
        }
        refreshNotification()
    }

    private fun refreshNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(TrackingState.state.value))
    }

    private fun buildNotification(s: LiveTracking): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "drive"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(a: String, code: Int) = PendingIntent.getService(this, code, Intent(this, TrackingService::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val miles = GeoMath.metersToMiles(s.verifiedMeters)
        val elapsed = s.elapsedMs(System.currentTimeMillis()) / 1000
        val q = GpsQuality.of(s.accuracyM, System.currentTimeMillis() - (s.lastFixTime ?: 0))
        val title = if (s.paused) "Trip paused · %.2f mi".format(miles) else "Recording trip · %.2f mi".format(miles)
        val text = "%d:%02d:%02d · GPS %s%s".format(elapsed / 3600, elapsed / 60 % 60, elapsed % 60, q.label,
            s.accuracyM?.let { " ±%.0f m".format(it) } ?: "") + if (s.gapMeters > 0) " · gaps %.1f mi (unverified)".format(GeoMath.metersToMiles(s.gapMeters)) else ""
        return NotificationCompat.Builder(this, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .apply {
                if (s.paused) addAction(0, "Resume", action(ACTION_RESUME, 2)) else addAction(0, "Pause", action(ACTION_PAUSE, 1))
                addAction(0, "Stop", action(ACTION_STOP, 3))
            }
            .build()
    }

    override fun onDestroy() {
        removeUpdates()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.mapledger.START"
        const val ACTION_PAUSE = "com.mapledger.PAUSE"
        const val ACTION_RESUME = "com.mapledger.RESUME"
        const val ACTION_STOP = "com.mapledger.STOP"
        const val ACTION_RECOVER = "com.mapledger.RECOVER"
        const val EXTRA_VEHICLE = "vehicle"
        const val EXTRA_ODO = "odometer"
        const val EXTRA_PHOTO = "photo"
        const val CHANNEL_TRACKING = "tracking"
        const val CHANNEL_ALERTS = "alerts"
        const val NOTIF_ID = 42
        const val NOTIF_INTERRUPTED = 43
        const val NOTIF_DRIVE = 44

        fun buildPath(points: List<Triple<LatLon, Int, Int>>): List<List<LatLon>> {
            val out = mutableListOf<MutableList<LatLon>>()
            var seg = -1
            points.forEach { (p, s, f) ->
                if (f == FixFlag.LOW_ACCURACY || f == FixFlag.DUPLICATE || f == FixFlag.IMPOSSIBLE_SPEED) return@forEach
                if (s != seg || f == FixFlag.GAP_BEFORE || out.isEmpty()) out += mutableListOf<LatLon>()
                out.last() += p; seg = s
            }
            return out
        }

        fun createChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_TRACKING, "Trip recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while MapLedger records a trip"; setShowBadge(false)
            })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERTS, "Tracking alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Interrupted recordings and drive detection prompts"
            })
        }

        fun postInterruptedNotification(ctx: Context) {
            val pi = PendingIntent.getActivity(ctx, 10, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "drive"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, CHANNEL_ALERTS).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Trip recording was interrupted")
                .setContentText("Your trip is saved. Tap to resume or finish it.")
                .setAutoCancel(true).setContentIntent(pi).build()
            if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_INTERRUPTED, n)
        }

        fun send(ctx: Context, action: String, block: Intent.() -> Unit = {}) {
            val i = Intent(ctx, TrackingService::class.java).setAction(action).apply(block)
            ContextCompat.startForegroundService(ctx, i)
        }
    }
}
