package com.mapledger.app.ui.components

import com.mapledger.app.domain.GeoMath
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val date = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
    private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE
    private val zone get() = ZoneId.systemDefault()

    fun money(cents: Long?): String = cents?.let { (if (it < 0) "-" else "") + "$" + "%,.2f".format(kotlin.math.abs(it) / 100.0) } ?: "—"
    fun money(d: Double?): String = d?.let { money(Math.round(it * 100)) } ?: "—"
    fun miles(m: Double, digits: Int = 1) = "%,.${digits}f mi".format(m)
    fun metersAsMiles(m: Double, digits: Int = 2) = miles(GeoMath.metersToMiles(m), digits)
    fun date(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(date)
    fun shortDate(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(shortDate)
    fun time(ms: Long?): String = ms?.let { Instant.ofEpochMilli(it).atZone(zone).format(time) } ?: "—"
    fun iso(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(iso)
    fun iso(d: LocalDate): String = d.format(iso)
    fun duration(ms: Long): String { val s = ms / 1000; return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) }
    fun hm(ms: Long): String { val m = ms / 60_000; return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }
    fun mph(mps: Float?) = mps?.let { "%.0f".format(it * 2.23694) } ?: "—"
    fun pct(f: Double) = "%.0f%%".format(f * 100)
    fun micros(m: Long) = "%,.4f".format(m / 1e6).trimEnd('0').trimEnd('.')
    fun localMidnight(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
}
