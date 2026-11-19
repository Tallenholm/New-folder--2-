package com.mapledger.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hive
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mapledger.app.ui.screens.*

private data class Dest(val route: String, val label: String, val icon: ImageVector)
private val DESTS = listOf(
    Dest("dashboard", "Home", Icons.Default.Dashboard),
    Dest("drive", "Drive", Icons.Default.DirectionsCar),
    Dest("trips", "Trips", Icons.Default.Route),
    Dest("bee", "Bee Maps", Icons.Default.Hive),
    Dest("money", "Money", Icons.Default.AttachMoney),
    Dest("tax", "Tax", Icons.AutoMirrored.Filled.ReceiptLong),
    Dest("settings", "Settings", Icons.Default.Settings),
)

/**
 * Adaptive navigation: bottom bar on phones, navigation rail on unfolded Galaxy Z Fold / tablets.
 */
@Composable
fun MapLedgerNav(startRoute: String?, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route?.substringBefore("?")?.substringBefore("/")
    fun go(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true; restoreState = true
    }
    LaunchedEffect(startRoute) { if (startRoute != null) { nav.navigate(startRoute) { launchSingleTop = true }; onRouteConsumed() } }

    NavigationSuiteScaffold(navigationSuiteItems = {
        DESTS.forEach { d ->
            item(selected = current == d.route, onClick = { go(d.route) }, icon = { Icon(d.icon, d.label) }, label = { Text(d.label) })
        }
    }) {
        Scaffold { pad ->
            NavHost(nav, startDestination = "dashboard", modifier = Modifier.fillMaxSize().padding(pad)) {
                composable("dashboard") { DashboardScreen(onNavigate = ::go) }
                composable("drive?confirmStart={confirmStart}", arguments = listOf(navArgument("confirmStart") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                    DriveScreen(confirmStart = it.arguments?.getString("confirmStart") == "1")
                }
                composable("trips") { TripsScreen(onOpen = { id -> nav.navigate("trip/$id") }) }
                composable("trip/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    TripDetailScreen(it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
                }
                composable("bee") { BeeScreen() }
                composable("money") { MoneyScreen() }
                composable("tax") { TaxScreen(onOpenTrip = { id -> nav.navigate("trip/$id") }) }
                composable("settings") { SettingsScreen() }
            }
        }
    }
}
