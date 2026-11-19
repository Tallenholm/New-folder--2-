package com.mapledger.app.data.repo

import com.mapledger.app.data.db.AppDatabase
import com.mapledger.app.data.db.AuditEntry
import com.mapledger.app.data.db.Expense
import com.mapledger.app.data.db.MileageRate
import com.mapledger.app.data.db.Payment
import com.mapledger.app.data.db.Vehicle
import com.mapledger.app.domain.RatePeriod
import java.time.LocalDate

class FinanceRepository(private val db: AppDatabase) {
    fun observeExpenses() = db.expenses().observeAll()
    fun observePayments() = db.payments().observeAll()
    fun observeVehicles() = db.vehicles().observeAll()
    fun observeRates() = db.rates().observeAll()

    suspend fun vehicles() = db.vehicles().all()
    suspend fun primaryVehicle() = db.vehicles().primary()

    suspend fun saveExpense(e: Expense): Long {
        return if (e.id == 0L) db.expenses().insert(e) else {
            val old = db.expenses().get(e.id)
            if (old != null && old != e) db.audit().insert(AuditEntry(entityType = "expense", entityId = e.id, field = "record", oldValue = old.toString(), newValue = e.toString(), reason = "Expense edited"))
            db.expenses().update(e); e.id
        }
    }
    suspend fun deleteExpense(id: Long) {
        db.expenses().get(id)?.let { db.audit().insert(AuditEntry(entityType = "expense", entityId = id, field = "deleted", oldValue = it.toString(), newValue = null)) }
        db.expenses().delete(id)
    }

    suspend fun savePayment(p: Payment): Long {
        return if (p.id == 0L) db.payments().insert(p) else {
            val old = db.payments().get(p.id)
            if (old != null && old != p) db.audit().insert(AuditEntry(entityType = "payment", entityId = p.id, field = "record", oldValue = old.toString(), newValue = p.toString(), reason = "Payment edited"))
            db.payments().update(p); p.id
        }
    }
    suspend fun deletePayment(id: Long) {
        db.payments().get(id)?.let { db.audit().insert(AuditEntry(entityType = "payment", entityId = id, field = "deleted", oldValue = it.toString(), newValue = null)) }
        db.payments().delete(id)
    }

    suspend fun saveVehicle(v: Vehicle): Long {
        if (v.isPrimary) db.vehicles().all().filter { it.isPrimary && it.id != v.id }.forEach { db.vehicles().update(it.copy(isPrimary = false)) }
        return if (v.id == 0L) db.vehicles().insert(v) else {
            val old = db.vehicles().get(v.id)
            if (old != null && old.deductionMethod != v.deductionMethod) db.audit().insert(AuditEntry(entityType = "vehicle", entityId = v.id,
                field = "deductionMethod", oldValue = old.deductionMethod.name, newValue = v.deductionMethod.name, reason = "Deduction method changed"))
            db.vehicles().update(v); v.id
        }
    }

    suspend fun rates(): List<RatePeriod> = db.rates().all().map {
        RatePeriod(LocalDate.ofEpochDay(it.fromEpochDay), LocalDate.ofEpochDay(it.toEpochDay), it.tenthsOfCent, it.label)
    }
    suspend fun addRate(from: LocalDate, to: LocalDate, tenths: Int, label: String) =
        db.rates().insert(MileageRate(fromEpochDay = from.toEpochDay(), toEpochDay = to.toEpochDay(), tenthsOfCent = tenths, label = label))
    suspend fun deleteRate(id: Long) = db.rates().delete(id)
}
