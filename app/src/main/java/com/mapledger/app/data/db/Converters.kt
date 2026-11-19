package com.mapledger.app.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun dm(v: DeductionMethod): String = v.name
    @TypeConverter fun dm(v: String): DeductionMethod = DeductionMethod.valueOf(v)
    @TypeConverter fun ts(v: TripStatus): String = v.name
    @TypeConverter fun ts(v: String): TripStatus = TripStatus.valueOf(v)
    @TypeConverter fun tc(v: TripClass): String = v.name
    @TypeConverter fun tc(v: String): TripClass = TripClass.valueOf(v)
    @TypeConverter fun rs(v: RecordSource): String = v.name
    @TypeConverter fun rs(v: String): RecordSource = RecordSource.valueOf(v)
    @TypeConverter fun us(v: UploadStatus): String = v.name
    @TypeConverter fun us(v: String): UploadStatus = UploadStatus.valueOf(v)
    @TypeConverter fun ec(v: ExpenseCategory): String = v.name
    @TypeConverter fun ec(v: String): ExpenseCategory = ExpenseCategory.valueOf(v)
    @TypeConverter fun pk(v: PaymentKind): String = v.name
    @TypeConverter fun pk(v: String): PaymentKind = PaymentKind.valueOf(v)
    @TypeConverter fun pc(v: PaymentCurrency): String = v.name
    @TypeConverter fun pc(v: String): PaymentCurrency = PaymentCurrency.valueOf(v)
    @TypeConverter fun ps(v: PaymentStatus): String = v.name
    @TypeConverter fun ps(v: String): PaymentStatus = PaymentStatus.valueOf(v)
    @TypeConverter fun bs(v: BurstStatus): String = v.name
    @TypeConverter fun bs(v: String): BurstStatus = BurstStatus.valueOf(v)
}
