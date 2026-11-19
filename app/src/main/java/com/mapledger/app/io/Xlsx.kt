package com.mapledger.app.io

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Dependency-free XLSX support: writes Office Open XML workbooks with inline strings/numbers,
 * and reads the first worksheet of existing ledgers (shared strings + inline strings + numbers).
 */
object Xlsx {
    sealed interface Cell { data class Text(val v: String) : Cell; data class Num(val v: Double) : Cell }
    data class Sheet(val name: String, val rows: List<List<Cell>>)

    fun text(v: String): Cell = Cell.Text(v)
    fun num(v: Double): Cell = Cell.Num(v)

    fun write(out: OutputStream, sheets: List<Sheet>) {
        ZipOutputStream(out).use { z ->
            fun put(name: String, body: String) { z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>""")
                append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
                sheets.indices.forEach { append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""") }
                append("</Types>")
            })
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                sheets.forEachIndexed { i, s -> append("""<sheet name="${Gpx.xml(s.name.take(31))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
                append("</sheets></workbook>")
            })
            put("xl/_rels/workbook.xml.rels", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                sheets.indices.forEach { append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""") }
                append("</Relationships>")
            })
            sheets.forEachIndexed { i, s ->
                put("xl/worksheets/sheet${i + 1}.xml", buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                    s.rows.forEachIndexed { r, row ->
                        append("<row r=\"${r + 1}\">")
                        row.forEachIndexed { c, cell ->
                            val ref = colName(c) + (r + 1)
                            when (cell) {
                                is Cell.Num -> if (cell.v.isFinite()) append("<c r=\"$ref\"><v>${cell.v}</v></c>")
                                is Cell.Text -> append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${Gpx.xml(cell.v)}</t></is></c>")
                            }
                        }
                        append("</row>")
                    }
                    append("</sheetData></worksheet>")
                })
            }
        }
    }

    fun colName(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val m = (n - 1) % 26; sb.insert(0, ('A' + m)); n = (n - 1) / 26 }
        return sb.toString()
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) { if (!ch.isLetter()) break; n = n * 26 + (ch.uppercaseChar() - 'A' + 1) }
        return n - 1
    }

    /** Reads the first worksheet into rows of strings (numbers rendered without trailing .0). */
    fun readFirstSheet(input: InputStream): List<List<String>> {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name == "xl/sharedStrings.xml" || e.name.startsWith("xl/worksheets/sheet")) files[e.name] = z.readBytes()
            }
        }
        val shared = mutableListOf<String>()
        files["xl/sharedStrings.xml"]?.let { bytes ->
            sax(bytes, object : DefaultHandler() {
                val sb = StringBuilder(); var inT = false; var inSi = false
                override fun startElement(u: String?, l: String?, q: String, a: Attributes) {
                    when (q.substringAfter(':')) { "si" -> { inSi = true; sb.clear() }; "t" -> inT = true }
                }
                override fun characters(ch: CharArray, s: Int, len: Int) { if (inT && inSi) sb.append(ch, s, len) }
                override fun endElement(u: String?, l: String?, q: String) {
                    when (q.substringAfter(':')) { "t" -> inT = false; "si" -> { shared += sb.toString(); inSi = false } }
                }
            })
        }
        val sheetName = files.keys.filter { it.startsWith("xl/worksheets/sheet") }
            .minWithOrNull(compareBy({ it.length }, { it })) ?: return emptyList()
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        sax(files.getValue(sheetName), object : DefaultHandler() {
            var row = 0; var col = 0; var type = ""; val v = StringBuilder(); var capture = false
            override fun startElement(u: String?, l: String?, q: String, a: Attributes) {
                when (q.substringAfter(':')) {
                    "row" -> row = (a.getValue("r")?.toIntOrNull() ?: (row + 1))
                    "c" -> { col = a.getValue("r")?.let { colIndex(it) } ?: (col + 1); type = a.getValue("t") ?: ""; v.clear() }
                    "v", "t" -> capture = true
                }
            }
            override fun characters(ch: CharArray, s: Int, len: Int) { if (capture) v.append(ch, s, len) }
            override fun endElement(u: String?, l: String?, q: String) {
                when (q.substringAfter(':')) {
                    "v", "t" -> capture = false
                    "c" -> {
                        val raw = v.toString()
                        val value = when (type) {
                            "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            else -> raw.toDoubleOrNull()?.let { d -> if (d == Math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString() } ?: raw
                        }
                        rows.getOrPut(row) { mutableMapOf() }[col] = value
                    }
                }
            }
        })
        return rows.values.map { m -> val max = (m.keys.maxOrNull() ?: -1); (0..max).map { m[it] ?: "" } }
    }

    private fun sax(bytes: ByteArray, h: DefaultHandler) {
        val f = SAXParserFactory.newInstance()
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        f.newSAXParser().parse(ByteArrayInputStream(bytes), h)
    }
}
