package com.mapledger.app.domain

import com.mapledger.app.data.db.Payment
import com.mapledger.app.data.db.PaymentCurrency
import com.mapledger.app.data.db.PaymentKind
import com.mapledger.app.data.db.PaymentStatus
import com.mapledger.app.util.Hash
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

/** Row-level mapping for CSV / XLSX imports. Pure Kotlin; no Android types. */
object Importers {

    data class Result<T>(val items: List<T>, val skipped: List<String>)

    private fun norm(h: String) = h.lowercase().replace(Regex("[^a-z0-9]"), "")

    /** Exact header match first, then substring match, skipping columns already claimed in [exclude]. */
    private fun col(headers: List<String>, vararg names: String, exclude: Set<Int> = emptySet()): Int {
        val n = headers.map(::norm)
        for (name in names) { val i = n.indices.firstOrNull { it !in exclude && n[it] == norm(name) }; if (i != null) return i }
        for (name in names) { val i = n.indices.firstOrNull { it !in exclude && n[it].contains(norm(name)) }; if (i != null) return i }
        return -1
    }

    private val DATE_FORMATS = listOf("M/d/yyyy", "MM/dd/yyyy", "M/d/yy", "yyyy-MM-dd", "yyyy/MM/dd", "MMM d, yyyy", "MMM d yyyy", "d-MMM-yyyy")
    private val DATETIME_FORMATS = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "M/d/yyyy H:mm", "M/d/yyyy H:mm:ss", "MM/dd/yyyy hh:mm a", "M/d/yyyy h:mm a")

    /** Parses ISO instants, unix seconds/millis, Excel serial dates and common US formats. */
    fun parseDate(raw: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val s = raw.trim().removeSuffix(" UTC")
        if (s.isEmpty()) return null
        runCatching { return Instant.parse(s).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        s.toDoubleOrNull()?.let { n ->
            return when {
                n > 1e12 -> n.toLong()                                   // epoch millis
                n > 1e9 -> (n * 1000).toLong()                            // epoch seconds
                n in 20000.0..80000.0 -> LocalDate.of(1899, 12, 30).plusDays(n.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() // Excel serial
                else -> null
            }
        }
        for (f in DATETIME_FORMATS) runCatching {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(f, java.util.Locale.US)).atZone(zone).toInstant().toEpochMilli()
        }
        for (f in DATE_FORMATS) runCatching {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern(f, java.util.Locale.US)).atStartOfDay(zone).toInstant().toEpochMilli()
        }
        return null
    }

    fun parseMoney(raw: String): Double? =
        raw.trim().replace("$", "").replace(",", "").replace("USD", "", true).replace("(", "-").replace(")", "").trim().toDoubleOrNull()

    /**
     * Payments CSV. Recognises MapLedger's own export, generic wallet / exchange exports and
     * Solana explorer token-transfer exports (Signature, Block Time, Amount, Token, Value...).
     */
    fun payments(rows: List<List<String>>, zone: ZoneId = ZoneId.systemDefault()): Result<Payment> {
        if (rows.isEmpty()) return Result(emptyList(), listOf("Empty file"))
        val h = rows.first()
        val iDate = col(h, "date", "blocktime", "humantime", "time", "timestamp", "received")
        val iAmt = col(h, "amount", "tokenamount", "quantity", "qty", "changeamount")
        val iCur = col(h, "currency", "token", "symbol", "asset", "tokenaddress")
        val iUsd = col(h, "usdfmv", "usdvalue", "valueusd", "fmv", "value", "usd")
        val iKind = col(h, "kind", "type", "category", "description", "action", "reward")
        val iTx = col(h, "txhash", "signature", "transaction", "txid", "hash")
        val iCamp = col(h, "campaign", "campaignid", "burst")
        val iFlow = col(h, "flow", "direction")
        if (iDate < 0 || iAmt < 0) return Result(emptyList(), listOf("Need at least Date and Amount columns; found: ${h.joinToString()}"))
        val out = mutableListOf<Payment>(); val skipped = mutableListOf<String>()
        rows.drop(1).forEachIndexed { idx, r ->
            fun g(i: Int) = if (i >= 0 && i < r.size) r[i].trim() else ""
            val line = idx + 2
            if (iFlow >= 0 && g(iFlow).lowercase() in setOf("out", "outgoing", "sent")) { skipped += "Row $line: outgoing transfer"; return@forEachIndexed }
            val date = parseDate(g(iDate), zone) ?: run { skipped += "Row $line: bad date '${g(iDate)}'"; return@forEachIndexed }
            val amt = parseMoney(g(iAmt)) ?: run { skipped += "Row $line: bad amount '${g(iAmt)}'"; return@forEachIndexed }
            if (amt <= 0) { skipped += "Row $line: non-positive amount"; return@forEachIndexed }
            val curRaw = g(iCur).uppercase()
            val cur = when {
                curRaw.contains("USDC") || curRaw.startsWith("EPJFWDD5") -> PaymentCurrency.USDC
                curRaw.contains("HONEY") || curRaw.startsWith("4VMSOUT") -> PaymentCurrency.HONEY
                curRaw.isEmpty() || curRaw == "USD" || curRaw == "$" -> PaymentCurrency.USD
                else -> PaymentCurrency.OTHER
            }
            val kindRaw = g(iKind).lowercase()
            val kind = when {
                kindRaw.contains("burst") -> PaymentKind.BURST
                kindRaw.contains("bonus") || kindRaw.contains("honey burst") -> PaymentKind.BONUS
                kindRaw.contains("refer") -> PaymentKind.REFERRAL
                kindRaw.isEmpty() || kindRaw.contains("map") || kindRaw.contains("base") || kindRaw.contains("reward") || kindRaw.contains("transfer") -> PaymentKind.BASE
                else -> PaymentKind.OTHER
            }
            val usd = when {
                iUsd >= 0 && iUsd != iAmt && g(iUsd).isNotBlank() -> parseMoney(g(iUsd))
                cur == PaymentCurrency.USD || cur == PaymentCurrency.USDC -> amt  // 1 USDC ≈ $1; user can edit
                else -> null
            }
            val tx = g(iTx)
            val micros = (amt * 1_000_000).roundToLong()
            out += Payment(
                date = date, kind = kind, currency = cur, amountMicros = micros,
                usdFmvCents = usd?.let { (it * 100).roundToLong() }, status = PaymentStatus.RECEIVED,
                campaignId = g(iCamp), txHash = tx,
                importHash = Hash.sha256(if (tx.isNotBlank()) "tx|$tx|$micros" else "row|$date|$micros|$cur|$kind"),
                notes = if (usd == null) "Imported — USD fair-market value needed" else "Imported",
            )
        }
        return Result(out, skipped)
    }

    data class LedgerRow(
        val date: Long, val startOdo: Double?, val endOdo: Double?, val totalMiles: Double?,
        val businessMiles: Double?, val personalMiles: Double?, val purpose: String, val from: String, val to: String,
        val hash: String,
    )

    /** Existing Excel / CSV mileage ledgers. Needs a Date column and either miles or odometer readings. */
    fun ledger(rows: List<List<String>>, zone: ZoneId = ZoneId.systemDefault()): Result<LedgerRow> {
        val headerIdx = rows.indexOfFirst { r -> r.any { norm(it).contains("date") } }
        if (headerIdx < 0) return Result(emptyList(), listOf("No header row with a Date column"))
        val h = rows[headerIdx]
        val iDate = col(h, "date", "tripdate")
        val iStart = col(h, "startodometer", "beginningodometer", "odometerstart", "startodo", "beginodo")
        val iEnd = col(h, "endodometer", "endingodometer", "odometerend", "endodo")
        val iBiz = col(h, "businessmiles", "business", "bizmiles", "deductiblemiles")
        val iPers = col(h, "personalmiles", "personal")
        val iTotal = col(h, "totalmiles", "miles", "distance", "mileage", exclude = setOf(iBiz, iPers, iStart, iEnd))
        val iPurpose = col(h, "purpose", "businesspurpose", "description", "notes")
        val iFrom = col(h, "from", "origin", "startlocation", "start", exclude = setOf(iStart))
        val iTo = col(h, "to", "destination", "endlocation", "end", exclude = setOf(iEnd, iTotal))
        val out = mutableListOf<LedgerRow>(); val skipped = mutableListOf<String>()
        rows.drop(headerIdx + 1).forEachIndexed { idx, r ->
            fun g(i: Int) = if (i >= 0 && i < r.size) r[i].trim() else ""
            fun d(i: Int) = parseMoney(g(i))
            val line = headerIdx + idx + 2
            val date = parseDate(g(iDate), zone) ?: run { skipped += "Row $line: no date"; return@forEachIndexed }
            val so = d(iStart); val eo = d(iEnd)
            val biz = d(iBiz)
            val total = d(iTotal) ?: if (so != null && eo != null && eo >= so) eo - so else null
            if (total == null && biz == null) { skipped += "Row $line: no miles or odometer"; return@forEachIndexed }
            if (so != null && eo != null && eo < so) { skipped += "Row $line: end odometer < start"; return@forEachIndexed }
            val purpose = g(iPurpose)
            out += LedgerRow(date, so, eo, total ?: biz, biz, d(iPers), purpose, g(iFrom), g(iTo),
                Hash.sha256("ledger|$date|$so|$eo|${total ?: biz}|$purpose"))
        }
        return Result(out, skipped)
    }

    fun approxEqual(a: Double, b: Double, tol: Double = 0.01) = abs(a - b) <= tol
}
