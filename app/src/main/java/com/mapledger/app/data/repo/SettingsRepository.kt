package com.mapledger.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    val homeLabel: String = "Home",
    val homeRadiusM: Double = 250.0,
    val homeApproximate: Boolean = true,
    val activeVehicleId: Long? = null,
    val biometricLock: Boolean = false,
    val driveDetection: Boolean = false,
    val marginalTaxRate: Double = 0.12,
    val missingPaymentDays: Long = 14,
    val maxAccuracyM: Double = 35.0,
    val seeded: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private object K {
        val homeLat = doublePreferencesKey("home_lat")
        val homeLon = doublePreferencesKey("home_lon")
        val homeLabel = stringPreferencesKey("home_label")
        val homeRadius = doublePreferencesKey("home_radius")
        val homeApprox = booleanPreferencesKey("home_approx")
        val vehicle = longPreferencesKey("active_vehicle")
        val bio = booleanPreferencesKey("biometric")
        val detect = booleanPreferencesKey("drive_detection")
        val taxRate = doublePreferencesKey("marginal_tax")
        val missingDays = longPreferencesKey("missing_days")
        val maxAcc = doublePreferencesKey("max_accuracy")
        val seeded = booleanPreferencesKey("seeded_v1")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            homeLat = p[K.homeLat], homeLon = p[K.homeLon],
            homeLabel = p[K.homeLabel] ?: "Home",
            homeRadiusM = p[K.homeRadius] ?: 250.0,
            homeApproximate = p[K.homeApprox] ?: true,
            activeVehicleId = p[K.vehicle],
            biometricLock = p[K.bio] ?: false,
            driveDetection = p[K.detect] ?: false,
            marginalTaxRate = p[K.taxRate] ?: 0.12,
            missingPaymentDays = p[K.missingDays] ?: 14,
            maxAccuracyM = p[K.maxAcc] ?: 35.0,
            seeded = p[K.seeded] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setHome(lat: Double, lon: Double, label: String, radiusM: Double, approximate: Boolean) =
        context.dataStore.edit {
            it[K.homeLat] = lat; it[K.homeLon] = lon; it[K.homeLabel] = label
            it[K.homeRadius] = radiusM; it[K.homeApprox] = approximate
        }
    suspend fun setActiveVehicle(id: Long) = context.dataStore.edit { it[K.vehicle] = id }
    suspend fun setBiometric(on: Boolean) = context.dataStore.edit { it[K.bio] = on }
    suspend fun setDriveDetection(on: Boolean) = context.dataStore.edit { it[K.detect] = on }
    suspend fun setMarginalTaxRate(r: Double) = context.dataStore.edit { it[K.taxRate] = r }
    suspend fun setMissingPaymentDays(d: Long) = context.dataStore.edit { it[K.missingDays] = d }
    suspend fun setMaxAccuracy(m: Double) = context.dataStore.edit { it[K.maxAcc] = m }
    suspend fun markSeeded() = context.dataStore.edit { it[K.seeded] = true }
}
