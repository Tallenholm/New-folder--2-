package com.mapledger.app.io

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/** A drive reconstructed from Google Maps Timeline. Points are sparse; distance is low-confidence. */
data class TimelineDrive(
    val startTime: Long,
    val endTime: Long,
    val points: List<GpxPoint>,
    val googleDistanceMeters: Double?,
    val activityType: String,
)

/**
 * Supports the three Google Timeline export shapes:
 *  1. On-device "Timeline.json" (2024+): semanticSegments[] with activity + timelinePath.
 *  2. Takeout "Semantic Location History" monthly files: timelineObjects[].activitySegment.
 *  3. Takeout "Records.json": raw locations[] (grouped into drives by time gaps + speed).
 * Only vehicle activities are returned; everything is imported UNCLASSIFIED for review.
 */
object Timeline {
    private val DRIVING = setOf("IN_PASSENGER_VEHICLE", "IN_VEHICLE", "DRIVING", "IN_CAR", "IN_TAXI", "MOTORCYCLING", "in passenger vehicle")

    fun parse(json: String): List<TimelineDrive> {
        val trimmed = json.trim()
        if (trimmed.startsWith("[")) return parseOnDevice(JSONArray(trimmed))
        val root = JSONObject(trimmed)
        return when {
            root.has("semanticSegments") -> parseOnDevice(root.getJSONArray("semanticSegments"))
            root.has("timelineObjects") -> parseSemantic(root.getJSONArray("timelineObjects"))
            root.has("locations") -> parseRecords(root.getJSONArray("locations"))
            else -> emptyList()
        }
    }

    private fun isDriving(type: String?) = type != null && (type.uppercase() in DRIVING.map { it.uppercase() } || type.uppercase().contains("VEHICLE"))

    private fun parseOnDevice(segs: JSONArray): List<TimelineDrive> {
        val out = mutableListOf<TimelineDrive>()
        // timelinePath points live in their own segments; collect them by time for lookup.
        val pathPoints = mutableListOf<GpxPoint>()
        for (i in 0 until segs.length()) {
            val s = segs.optJSONObject(i) ?: continue
            val path = s.optJSONArray("timelinePath") ?: continue
            for (j in 0 until path.length()) {
                val p = path.optJSONObject(j) ?: continue
                val ll = parseLatLng(p.optString("point")) ?: continue
                pathPoints += GpxPoint(ll.first, ll.second, time(p.optString("time")))
            }
        }
        pathPoints.sortBy { it.time ?: 0 }
        for (i in 0 until segs.length()) {
            val s = segs.optJSONObject(i) ?: continue
            val act = s.optJSONObject("activity") ?: continue
            val type = act.optJSONObject("topCandidate")?.optString("type")
            if (!isDriving(type)) continue
            val st = time(s.optString("startTime")) ?: continue
            val et = time(s.optString("endTime")) ?: continue
            val start = parseLatLng(act.optJSONObject("start")?.optString("latLng"))
            val end = parseLatLng(act.optJSONObject("end")?.optString("latLng"))
            val pts = mutableListOf<GpxPoint>()
            start?.let { pts += GpxPoint(it.first, it.second, st) }
            pts += pathPoints.filter { (it.time ?: 0) in (st + 1) until et }
            end?.let { pts += GpxPoint(it.first, it.second, et) }
            val dist = if (act.has("distanceMeters")) act.optDouble("distanceMeters") else null
            out += TimelineDrive(st, et, pts, dist, type ?: "IN_PASSENGER_VEHICLE")
        }
        return out
    }

    private fun parseSemantic(objs: JSONArray): List<TimelineDrive> {
        val out = mutableListOf<TimelineDrive>()
        for (i in 0 until objs.length()) {
            val seg = objs.optJSONObject(i)?.optJSONObject("activitySegment") ?: continue
            val type = seg.optString("activityType")
            if (!isDriving(type)) continue
            val dur = seg.optJSONObject("duration") ?: continue
            val st = time(dur.optString("startTimestamp")) ?: dur.optString("startTimestampMs").toLongOrNull() ?: continue
            val et = time(dur.optString("endTimestamp")) ?: dur.optString("endTimestampMs").toLongOrNull() ?: continue
            val pts = mutableListOf<GpxPoint>()
            seg.optJSONObject("startLocation")?.let { e7(it)?.let { ll -> pts += GpxPoint(ll.first, ll.second, st) } }
            val raw = seg.optJSONObject("simplifiedRawPath")?.optJSONArray("points")
                ?: seg.optJSONObject("waypointPath")?.optJSONArray("waypoints")
            if (raw != null) for (j in 0 until raw.length()) {
                val p = raw.optJSONObject(j) ?: continue
                val ll = e7(p) ?: continue
                val t = time(p.optString("timestamp")) ?: p.optString("timestampMs").toLongOrNull()
                pts += GpxPoint(ll.first, ll.second, t)
            }
            seg.optJSONObject("endLocation")?.let { e7(it)?.let { ll -> pts += GpxPoint(ll.first, ll.second, et) } }
            val dist = if (seg.has("distance")) seg.optDouble("distance") else null
            out += TimelineDrive(st, et, pts, dist, type)
        }
        return out
    }

    /** Raw records: split where the time gap exceeds 10 min; keep chunks that look like driving. */
    private fun parseRecords(locs: JSONArray): List<TimelineDrive> {
        val pts = mutableListOf<GpxPoint>()
        for (i in 0 until locs.length()) {
            val o = locs.optJSONObject(i) ?: continue
            val ll = e7(o) ?: continue
            val t = time(o.optString("timestamp")) ?: o.optString("timestampMs").toLongOrNull() ?: continue
            pts += GpxPoint(ll.first, ll.second, t)
        }
        pts.sortBy { it.time }
        val chunks = mutableListOf<MutableList<GpxPoint>>()
        for (p in pts) {
            val last = chunks.lastOrNull()?.lastOrNull()
            if (last == null || (p.time!! - last.time!!) > 10 * 60_000) chunks += mutableListOf(p) else chunks.last() += p
        }
        return chunks.mapNotNull { c ->
            if (c.size < 3) return@mapNotNull null
            var d = 0.0
            for (k in 1 until c.size) d += com.mapledger.app.domain.GeoMath.haversineMeters(c[k - 1].lat, c[k - 1].lon, c[k].lat, c[k].lon)
            val hours = (c.last().time!! - c.first().time!!) / 3_600_000.0
            val avgKmh = if (hours > 0) d / 1000 / hours else 0.0
            if (d < 1000 || avgKmh < 15) null else TimelineDrive(c.first().time!!, c.last().time!!, c, null, "INFERRED_FROM_RECORDS")
        }
    }

    private fun e7(o: JSONObject): Pair<Double, Double>? {
        val lat = o.optLong("latitudeE7", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: o.optLong("latE7", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return null
        val lon = o.optLong("longitudeE7", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: o.optLong("lngE7", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return null
        return lat / 1e7 to lon / 1e7
    }

    /** "41.2503°, -87.8314°" or "geo:41.25,-87.83" */
    fun parseLatLng(s: String?): Pair<Double, Double>? {
        if (s.isNullOrBlank()) return null
        val parts = s.removePrefix("geo:").replace("°", "").split(",").map { it.trim() }
        if (parts.size != 2) return null
        val a = parts[0].toDoubleOrNull() ?: return null
        val b = parts[1].toDoubleOrNull() ?: return null
        return a to b
    }

    private fun time(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
    }
}
