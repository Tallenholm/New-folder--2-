package com.mapledger.app.io

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.io.Writer
import java.time.Instant
import java.time.OffsetDateTime
import javax.xml.parsers.SAXParserFactory

data class GpxPoint(val lat: Double, val lon: Double, val time: Long? = null, val ele: Double? = null, val name: String? = null, val desc: String? = null)
data class GpxTrack(val name: String, val segments: List<List<GpxPoint>>)
data class GpxRoute(val name: String, val points: List<GpxPoint>)
data class GpxData(val tracks: List<GpxTrack>, val routes: List<GpxRoute>, val waypoints: List<GpxPoint>)

object Gpx {
    fun parse(input: InputStream): GpxData {
        val tracks = mutableListOf<GpxTrack>()
        val routes = mutableListOf<GpxRoute>()
        val wpts = mutableListOf<GpxPoint>()
        val handler = object : DefaultHandler() {
            var text = StringBuilder()
            var curTrkName: String? = null
            var curSegs: MutableList<List<GpxPoint>>? = null
            var curSeg: MutableList<GpxPoint>? = null
            var curRteName: String? = null
            var curRte: MutableList<GpxPoint>? = null
            var pLat = 0.0; var pLon = 0.0; var pTime: Long? = null; var pEle: Double? = null
            var pName: String? = null; var pDesc: String? = null
            var inPoint = false

            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                text = StringBuilder()
                when (local(qName, localName)) {
                    "trk" -> { curSegs = mutableListOf(); curTrkName = null }
                    "trkseg" -> curSeg = mutableListOf()
                    "rte" -> { curRte = mutableListOf(); curRteName = null }
                    "trkpt", "rtept", "wpt" -> {
                        inPoint = true
                        pLat = a.getValue("lat")?.toDoubleOrNull() ?: Double.NaN
                        pLon = a.getValue("lon")?.toDoubleOrNull() ?: Double.NaN
                        pTime = null; pEle = null; pName = null; pDesc = null
                    }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { text.append(ch, start, length) }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                val t = text.toString().trim()
                when (local(qName, localName)) {
                    "time" -> if (inPoint) pTime = parseTime(t)
                    "ele" -> if (inPoint) pEle = t.toDoubleOrNull()
                    "name" -> when {
                        inPoint -> pName = t
                        curRte != null -> curRteName = t
                        curSegs != null -> curTrkName = t
                    }
                    "desc" -> if (inPoint) pDesc = t
                    "trkpt" -> { inPoint = false; point()?.let { curSeg?.add(it) } }
                    "rtept" -> { inPoint = false; point()?.let { curRte?.add(it) } }
                    "wpt" -> { inPoint = false; point()?.let { wpts.add(it) } }
                    "trkseg" -> { curSeg?.let { if (it.isNotEmpty()) curSegs?.add(it) }; curSeg = null }
                    "trk" -> { curSegs?.let { tracks.add(GpxTrack(curTrkName ?: "Track ${tracks.size + 1}", it)) }; curSegs = null }
                    "rte" -> { curRte?.let { routes.add(GpxRoute(curRteName ?: "Route ${routes.size + 1}", it)) }; curRte = null }
                }
            }
            private fun point(): GpxPoint? =
                if (pLat.isNaN() || pLon.isNaN() || pLat !in -90.0..90.0 || pLon !in -180.0..180.0) null
                else GpxPoint(pLat, pLon, pTime, pEle, pName, pDesc)
        }
        val f = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        f.newSAXParser().parse(input, handler)
        return GpxData(tracks, routes, wpts)
    }

    private fun local(q: String, l: String?) = (l?.takeIf { it.isNotEmpty() } ?: q).substringAfter(':')

    fun parseTime(s: String): Long? = runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()

    data class ExportTrack(val name: String, val segments: List<List<GpxPoint>>)

    fun write(w: Writer, tracks: List<ExportTrack>, waypoints: List<GpxPoint> = emptyList()) {
        w.write("""<?xml version="1.0" encoding="UTF-8"?>""" + "\n")
        w.write("""<gpx version="1.1" creator="MapLedger" xmlns="http://www.topografix.com/GPX/1/1">""" + "\n")
        waypoints.forEach { p ->
            w.write("""  <wpt lat="${p.lat}" lon="${p.lon}"><name>${xml(p.name ?: "")}</name></wpt>""" + "\n")
        }
        tracks.forEach { t ->
            w.write("  <trk><name>${xml(t.name)}</name>\n")
            t.segments.forEach { seg ->
                w.write("    <trkseg>\n")
                seg.forEach { p ->
                    w.write("""      <trkpt lat="${p.lat}" lon="${p.lon}">""")
                    p.ele?.let { w.write("<ele>$it</ele>") }
                    p.time?.let { w.write("<time>${Instant.ofEpochMilli(it)}</time>") }
                    w.write("</trkpt>\n")
                }
                w.write("    </trkseg>\n")
            }
            w.write("  </trk>\n")
        }
        w.write("</gpx>\n")
        w.flush()
    }

    fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
