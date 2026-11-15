package com.example.orbitalcommand.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.orbitalcommand.model.MissionEvent
import com.example.orbitalcommand.notifications.OrbitalNotificationCenter

/**
 * Determines whether an alert's Inspect action should open Operations.
 * Fault-only alerts continue to open the Fleet page.
 */
object MissionAlertRouting {
    fun isRepairRelated(event: MissionEvent): Boolean {
        if (event.sourceKey == "EQUIPMENT_DAMAGE") return true
        if (event.sourceKey == "MANUAL_FAULT" ||
            event.sourceKey == "POWER_STATUS") return false

        val text = "${event.title} ${event.description}".lowercase()
        return listOf(
            "repair", "work order", "maintenance", "servicing",
            "equipment damage", "solar panel damage", "dish damage",
            "payload damage", "thermal damage", "module replacement"
        ).any { text.contains(it) }
    }
}

/**
 * Registers notification channels, requests Android 13+ notification permission
 * once. The ViewModel publishes new events independently of UI lifecycle,
 * so notifications can still appear while the app is backgrounded.
 * Terminated-app remote delivery requires FCM and a server-side source.
 */
@Composable
fun MissionNotificationEffects() {
    val context = LocalContext.current.applicationContext
    val center = remember(context) { OrbitalNotificationCenter(context) }
    val prefs = remember(context) {
        context.getSharedPreferences("orbital_command_notifications", Context.MODE_PRIVATE)
    }

    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Android manages the permission decision. */ }

    LaunchedEffect(Unit) {
        center.createChannels()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("permission_prompted", false)
        ) {
            prefs.edit().putBoolean("permission_prompted", true).apply()
            permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

}

/**
 * Consumes a notification's launch destination once, after dashboard state
 * is initialized. The selected page is saved by the dashboard's selectPage().
 */
@Composable
fun MissionNotificationRedirect(
    onSelectPage: (Int) -> Unit,
    onSelectSatellite: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findHostActivity() }

    LaunchedEffect(activity) {
        val intent = activity?.intent ?: return@LaunchedEffect
        if (!intent.hasExtra(OrbitalNotificationCenter.EXTRA_PAGE)) return@LaunchedEffect

        val page = intent.getIntExtra(OrbitalNotificationCenter.EXTRA_PAGE, 0)
            .coerceIn(0, 2)
        val satellite = intent.getStringExtra(OrbitalNotificationCenter.EXTRA_SATELLITE)

        onSelectPage(page)
        if (!satellite.isNullOrBlank() && satellite != "FLEET") {
            onSelectSatellite(satellite)
        }

        intent.removeExtra(OrbitalNotificationCenter.EXTRA_PAGE)
        intent.removeExtra(OrbitalNotificationCenter.EXTRA_SATELLITE)
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
