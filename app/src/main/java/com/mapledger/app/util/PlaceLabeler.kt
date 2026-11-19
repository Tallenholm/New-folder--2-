package com.mapledger.app.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Best-effort reverse geocoding. Offline or failure → coordinates (never blocks trip saving). */
class PlaceLabeler(private val context: Context) {
    suspend fun label(lat: Double, lon: Double): String {
        val fallback = "%.5f, %.5f".format(lat, lon)
        if (!Geocoder.isPresent()) return fallback
        val addr = withTimeoutOrNull(6_000) { lookup(lat, lon) } ?: return fallback
        return listOfNotNull(addr.thoroughfare?.let { (addr.subThoroughfare?.let { n -> "$n " } ?: "") + it }, addr.locality, addr.adminArea)
            .joinToString(", ").ifBlank { fallback }
    }

    @Suppress("DEPRECATION")
    private suspend fun lookup(lat: Double, lon: Double): Address? {
        val g = Geocoder(context, Locale.US)
        return if (Build.VERSION.SDK_INT >= 33) suspendCancellableCoroutine { cont ->
            try {
                g.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses.firstOrNull()) }
                    override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(null) }
                })
            } catch (e: Exception) { if (cont.isActive) cont.resume(null) }
        } else withContext(Dispatchers.IO) { runCatching { g.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull() }
    }
}
