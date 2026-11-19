package com.mapledger.app.io

import com.mapledger.app.domain.GeoMath
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter

class FormatsTest {
    @Test fun gpxRoundTrip() {
        val pts = listOf(GpxPoint(41.25, -87.83, 1_790_000_000_000L), GpxPoint(41.26, -87.83, 1_790_000_060_000L))
        val w = StringWriter()
        Gpx.write(w, listOf(Gpx.ExportTrack("Trip & <test>", listOf(pts, pts))), listOf(GpxPoint(41.3, -87.9, name = "Burst A")))
        val parsed = Gpx.parse(ByteArrayInputStream(w.toString().toByteArray()))
        assertEquals(1, parsed.tracks.size)
        assertEquals("Trip & <test>", parsed.tracks[0].name)
        assertEquals(2, parsed.tracks[0].segments.size)
        assertEquals(1_790_000_060_000L, parsed.tracks[0].segments[0][1].time)
        assertEquals("Burst A", parsed.waypoints.single().name)
    }

    @Test fun gpxWithNamespacesAndRoutes() {
        val xml = """<?xml version="1.0"?><gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1">
            <rte><name>Planned</name><rtept lat="41.1" lon="-87.1"/><rtept lat="41.2" lon="-87.2"/></rte>
            <trk><trkseg><trkpt lat="999" lon="0"/><trkpt lat="41.0" lon="-87.0"><time>2026-10-05T12:00:00Z</time></trkpt></trkseg></trk></gpx>"""
        val g = Gpx.parse(ByteArrayInputStream(xml.toByteArray()))
        assertEquals("Planned", g.routes.single().name)
        assertEquals(2, g.routes.single().points.size)
        assertEquals("invalid coordinates dropped", 1, g.tracks.single().segments.single().size)
    }

    @Test fun timelineOnDeviceFormat() {
        val json = """{"semanticSegments":[
          {"startTime":"2026-10-05T08:00:00.000-05:00","endTime":"2026-10-05T08:30:00.000-05:00",
           "activity":{"start":{"latLng":"41.2506°, -87.8314°"},"end":{"latLng":"41.1200°, -87.8610°"},"distanceMeters":15000.0,
           "topCandidate":{"type":"IN_PASSENGER_VEHICLE","probability":0.9}}},
          {"startTime":"2026-10-05T08:00:00.000-05:00","endTime":"2026-10-05T09:00:00.000-05:00",
           "timelinePath":[{"point":"41.2000°, -87.8400°","time":"2026-10-05T08:10:00.000-05:00"},{"point":"41.1600°, -87.8500°","time":"2026-10-05T08:20:00.000-05:00"}]},
          {"startTime":"2026-10-05T09:00:00.000-05:00","endTime":"2026-10-05T09:10:00.000-05:00",
           "activity":{"start":{"latLng":"41.12°, -87.86°"},"end":{"latLng":"41.121°, -87.861°"},"topCandidate":{"type":"WALKING"}}}
        ]}"""
        val drives = Timeline.parse(json)
        assertEquals(1, drives.size)
        assertEquals(4, drives[0].points.size)
        assertEquals(15000.0, drives[0].googleDistanceMeters!!, 0.0)
    }

    @Test fun timelineSemanticAndRecords() {
        val sem = """{"timelineObjects":[{"activitySegment":{"activityType":"IN_PASSENGER_VEHICLE","distance":5000,
            "startLocation":{"latitudeE7":412506000,"longitudeE7":-878314000},"endLocation":{"latitudeE7":412906000,"longitudeE7":-878314000},
            "duration":{"startTimestamp":"2026-10-05T13:00:00Z","endTimestamp":"2026-10-05T13:10:00Z"},
            "simplifiedRawPath":{"points":[{"latE7":412706000,"lngE7":-878314000,"timestamp":"2026-10-05T13:05:00Z"}]}}}]}"""
        val d = Timeline.parse(sem).single()
        assertEquals(3, d.points.size)
        val recs = buildString {
            append("""{"locations":[""")
            append((0 until 60).joinToString(",") { i -> """{"latitudeE7":${412506000 + i * 1000},"longitudeE7":-878314000,"timestamp":"2026-10-05T13:${"%02d".format(i)}:00Z"}""" })
            append("]}")
        }
        // 60 points × 111 m per minute ≈ 6.6 km/h: too slow to be a drive → not imported.
        assertEquals(0, Timeline.parse(recs).size)
    }

    @Test fun xlsxWriteThenRead() {
        val out = ByteArrayOutputStream()
        Xlsx.write(out, listOf(Xlsx.Sheet("Log", listOf(
            listOf(Xlsx.text("Date"), Xlsx.text("Miles"), Xlsx.text("Purpose")),
            listOf(Xlsx.text("2026-10-05"), Xlsx.num(43.9), Xlsx.text("Bee Maps & bursts")),
            listOf(Xlsx.text("2026-10-06"), Xlsx.num(12.0), Xlsx.text("<route>")),
        ))))
        val rows = Xlsx.readFirstSheet(ByteArrayInputStream(out.toByteArray()))
        assertEquals(listOf("Date", "Miles", "Purpose"), rows[0])
        assertEquals(listOf("2026-10-05", "43.9", "Bee Maps & bursts"), rows[1])
        assertEquals("12", rows[2][1])
        assertEquals("AA", Xlsx.colName(26))
    }

    @Test fun backupEncryptionRoundTripAndWrongPassword() {
        val data = "secret location history".toByteArray()
        val blob = BackupCrypto.encrypt(data, "correct horse".toCharArray())
        assertFalse(String(blob).contains("secret"))
        assertArrayEquals(data, BackupCrypto.decrypt(blob, "correct horse".toCharArray()))
        assertThrows(javax.crypto.AEADBadTagException::class.java) { BackupCrypto.decrypt(blob, "wrong".toCharArray()) }
        val tampered = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertThrows(javax.crypto.AEADBadTagException::class.java) { BackupCrypto.decrypt(tampered, "correct horse".toCharArray()) }
    }

    @Test fun kmToMiles() = assertEquals(43.93, GeoMath.kmToMiles(70.7), 0.01)
}
