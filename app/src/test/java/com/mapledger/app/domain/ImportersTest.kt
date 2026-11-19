package com.mapledger.app.domain

import com.mapledger.app.data.db.PaymentCurrency
import com.mapledger.app.data.db.PaymentKind
import com.mapledger.app.io.Csv
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ImportersTest {
    private val zone = ZoneId.of("America/Chicago")

    @Test fun parsesManyDateFormats() {
        val expect = LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        listOf("10/5/2026", "2026-10-05", "Oct 5, 2026", "46300").forEach {
            assertEquals(it, expect, Importers.parseDate(it, zone))
        }
        assertEquals(1_790_000_000_000L, Importers.parseDate("1790000000", zone))
        assertNotNull(Importers.parseDate("2026-10-05T14:03:00Z", zone))
    }

    @Test fun solanaExplorerExportMapsToPayments() {
        val csv = """
            Signature,Block Time,Human Time,Action,From,To,Amount,Flow,Value,Decimals,Token Address
            5abc,1791234567,2026-10-04T12:29:27.000Z,TRANSFER,Fee,Me,12.5,in,12.48,6,EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v
            5def,1791334567,2026-10-05T16:16:07.000Z,TRANSFER,Me,Other,3,out,3,6,EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v
            5ghi,1791434567,2026-10-06T20:02:47.000Z,TRANSFER,Fee,Me,800,in,,9,4vMsoUT2BWatFweudnQM1xedRLfJgJ7hswhcpz4xgBTy
        """.trimIndent()
        val r = Importers.payments(Csv.parse(csv), zone)
        assertEquals(2, r.items.size)
        assertEquals(1, r.skipped.size) // outgoing
        assertEquals(PaymentCurrency.USDC, r.items[0].currency)
        assertEquals(12_500_000L, r.items[0].amountMicros)
        assertEquals(1248L, r.items[0].usdFmvCents)
        assertEquals(PaymentCurrency.HONEY, r.items[1].currency)
        assertNull("HONEY with no value needs FMV", r.items[1].usdFmvCents)
    }

    @Test fun duplicateRowsProduceSameHash() {
        val csv = "date,amount,currency,type,tx\n2026-10-05,10,USDC,burst,abc\n2026-10-05,10,USDC,burst,abc\n"
        val r = Importers.payments(Csv.parse(csv), zone)
        assertEquals(2, r.items.size)
        assertEquals(r.items[0].importHash, r.items[1].importHash)
        assertEquals(PaymentKind.BURST, r.items[0].kind)
    }

    @Test fun ledgerWithOdometerAndMiles() {
        val csv = """
            Mileage Log 2026,,,,,
            Date,Start Odometer,End Odometer,Business Miles,Purpose,Destination
            10/5/2026,12000,12043.9,43.9,Bee Maps mapping,Kankakee
            10/6/2026,,,12.5,Burst collection,Bourbonnais
            10/7/2026,12100,12050,,bad,row
            not a date,1,2,3,x,y
        """.trimIndent()
        val r = Importers.ledger(Csv.parse(csv), zone)
        assertEquals(2, r.items.size)
        assertEquals(43.9, r.items[0].totalMiles!!, 1e-6)
        assertEquals(12.5, r.items[1].businessMiles!!, 1e-6)
        assertEquals(2, r.skipped.size)
        assertNotEquals(r.items[0].hash, r.items[1].hash)
        // Re-parsing gives identical hashes → duplicate import detection works across runs.
        assertEquals(r.items.map { it.hash }, Importers.ledger(Csv.parse(csv), zone).items.map { it.hash })
    }

    @Test fun csvQuotesAndCommas() {
        val rows = Csv.parse("a,\"b, c\",\"d \"\"q\"\"\"\r\n1,2,3")
        assertEquals(listOf("a", "b, c", "d \"q\""), rows[0])
        assertEquals(listOf("1", "2", "3"), rows[1])
    }
}
