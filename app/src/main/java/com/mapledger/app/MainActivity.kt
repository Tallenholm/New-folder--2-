package com.mapledger.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.mapledger.app.data.db.TripStatus
import com.mapledger.app.tracking.DriveDetectionReceiver
import com.mapledger.app.tracking.TrackingService
import com.mapledger.app.tracking.TrackingState
import com.mapledger.app.ui.MapLedgerNav
import com.mapledger.app.ui.screens.hasPreciseLocation
import com.mapledger.app.ui.theme.Honey
import com.mapledger.app.ui.theme.MapLedgerTheme
import kotlinx.coroutines.launch

/** FragmentActivity (a ComponentActivity) so BiometricPrompt can attach. */
class MainActivity : FragmentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)
    private var unlocked by mutableStateOf(false)
    private var lockRequired by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)
        val c = (application as MapLedgerApp).container
        lifecycleScope.launch {
            c.seedIfNeeded()
            val s = c.settings.current()
            lockRequired = s.biometricLock
            if (s.biometricLock) authenticate() else unlocked = true
            if (s.driveDetection) DriveDetectionReceiver.enable(this@MainActivity)
            // A trip still marked RECORDING while no service runs = process was killed. Reattach
            // from the foreground (allowed) so recording continues without losing the trip.
            val active = c.trips.active()
            if (active != null && active.status == TripStatus.RECORDING && !TrackingState.state.value.running && hasPreciseLocation(this@MainActivity)) {
                TrackingService.send(this@MainActivity, TrackingService.ACTION_RECOVER)
            }
        }
        setContent {
            MapLedgerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        lockRequired == null -> {}
                        unlocked -> MapLedgerNav(pendingRoute) { pendingRoute = null }
                        else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MapLedger is locked", style = MaterialTheme.typography.headlineSmall, color = Honey)
                            Button(onClick = ::authenticate) { Text("Unlock") }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute = it }
    }

    private fun authenticate() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock MapLedger")
            .setSubtitle("Your mileage and location history are protected").setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL).build())
    }

    companion object { const val EXTRA_ROUTE = "route" }
}
