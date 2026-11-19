package com.mapledger.app.io

import com.mapledger.app.data.db.*
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupJsonTest {
    @Test fun snapshotRoundTripIsLossless() {
        val s = BackupJson.Snapshot(
            vehicles = listOf(Vehicle(1, "Elantra", 2025, "Hyundai", "Elantra Hybrid", "Limited", true, DeductionMethod.STANDARD_MILEAGE, 2026, false, true, "")),
            trips = listOf(Trip(id = 7, vehicleId = 1, status = TripStatus.COMPLETED, source = RecordSource.LIVE_GPS, startTime = 1, endTime = 2,
                startLat = 41.25, startLon = -87.83, gpsMeters = 1234.5, adjustedMiles = null, classification = TripClass.MIXED, businessMiles = 0.5,
                needsTaxReview = true, importHash = null, createdAt = 3, updatedAt = 4)),
            points = listOf(TrackPoint(1, 7, 0, 1000, 41.25, -87.83, 4.5f, null, 12.5f, null, 0)),
            audit = listOf(AuditEntry(1, "trip", 7, 5, "classification", "UNCLASSIFIED", "MIXED", "why")),
            expenses = listOf(Expense(1, null, 10, ExpenseCategory.PHONE_DATA, 6000, "Verizon", 50, null, null, null, "", 11)),
            payments = listOf(Payment(1, 20, PaymentKind.BURST, PaymentCurrency.USDC, 5_000_000, null, PaymentStatus.RECEIVED, 500, "C1", 3, "tx", "", "h", 21)),
            routes = listOf(PlannedRoute(1, "R", 30, "f.gpx", 100.0, 2, "hash")),
            routePoints = listOf(RoutePoint(1, 1, 0, 41.0, -87.0, null), RoutePoint(2, 1, 1, 41.1, -87.0, 7)),
            targets = listOf(BurstTarget(3, "T", 41.0, -87.0, 75.0, "C1", 500, BurstStatus.CREDITED, 7, 1000, 2, "")),
            goals = listOf(HistoricalGoal(1, 20_731, 70.7, "Bee Maps", null, "")),
            rates = listOf(MileageRate(1, 20_454, 20_634, 725, "IRS")),
        )
        val back = BackupJson.decode(BackupJson.encode(s))
        assertEquals(s.vehicles, back.vehicles); assertEquals(s.trips, back.trips); assertEquals(s.points, back.points)
        assertEquals(s.audit, back.audit); assertEquals(s.expenses, back.expenses); assertEquals(s.payments, back.payments)
        assertEquals(s.routes, back.routes); assertEquals(s.routePoints, back.routePoints); assertEquals(s.targets, back.targets)
        assertEquals(s.goals, back.goals); assertEquals(s.rates, back.rates)
    }
}
