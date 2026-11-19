package com.mapledger.app.tracking

import com.mapledger.app.domain.LatLon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GpsQuality(val label: String) { NONE("No fix"), POOR("Poor"), FAIR("Fair"), GOOD("Good"), EXCELLENT("Excellent");
    companion object {
        fun of(acc: Float?, ageMs: Long): GpsQuality = when {
            acc == null || ageMs > 30_000 -> NONE
            acc <= 6 -> EXCELLENT
            acc <= 15 -> GOOD
            acc <= 35 -> FAIR
            else -> POOR
        }
    }
}

data class LiveTracking(
    val running: Boolean = false,
    val paused: Boolean = false,
    val tripId: Long? = null,
    val startTime: Long = 0,
    val pausedMs: Long = 0,
    val pausedAt: Long? = null,
    val verifiedMeters: Double = 0.0,
    val gapMeters: Double = 0.0,
    val speedMps: Float? = null,
    val accuracyM: Float? = null,
    val lastFixTime: Long? = null,
    val acceptedFixes: Int = 0,
    val rejectedFixes: Int = 0,
    /** Accepted path, split into segments at pauses/gaps so the map never draws unsupported lines. */
    val path: List<List<LatLon>> = emptyList(),
    val error: String? = null,
) {
    fun elapsedMs(now: Long) = if (startTime == 0L) 0 else (now - startTime - pausedMs - (pausedAt?.let { now - it } ?: 0)).coerceAtLeast(0)
}

/** Process-wide live state published by [TrackingService] and observed by the UI. */
object TrackingState {
    private val _state = MutableStateFlow(LiveTracking())
    val state: StateFlow<LiveTracking> = _state.asStateFlow()
    fun update(f: (LiveTracking) -> LiveTracking) { _state.value = f(_state.value) }
    fun reset() { _state.value = LiveTracking() }
}
