package com.example.orbitalcommand.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.orbitalcommand.model.MissionEvent
import com.example.orbitalcommand.model.MissionEventKind
import com.example.orbitalcommand.model.MissionEventSeverity
import com.example.orbitalcommand.ui.MissionAlertRouting

/**
 * Publishes newly recorded on-device simulation events as Android notifications.
 *
 * The event ID is a high-water mark so configuration changes, screen navigation,
 * and process restoration do not replay old mission history as new notifications.
 * This is LOCAL notification delivery, not a cloud-sent FCM message.
 */
class OrbitalNotificationCenter(context: Context) {

    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS = "orbital_command_notifications"
        private const val LAST_EVENT_ID = "last_processed_event_id"
        private const val FAULT_CHANNEL = "orbital_faults"
        private const val MISSION_CHANNEL = "orbital_mission_alerts"

        const val EXTRA_PAGE = "orbital_notification_page"
        const val EXTRA_SATELLITE = "orbital_notification_satellite"
    }

    /** Android 8+ requires a notification channel before any notification is posted. */
    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        manager.createNotificationChannel(
            NotificationChannel(
                FAULT_CHANNEL,
                "Satellite faults",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New satellite faults and critical power warnings"
                enableVibration(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                MISSION_CHANNEL,
                "Mission alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Repair, servicing, and mission warning events"
            }
        )
    }

    /**
     * Only publish events that appeared since the last observed event ID.
     * On the first launch after installation, establish a baseline from the
     * existing history rather than spamming notifications for old missions.
     */
    fun publishNewEvents(events: List<MissionEvent>) {
        if (events.isEmpty()) return

        val newestId = events.maxOf { it.id }

        if (!preferences.contains(LAST_EVENT_ID)) {
            preferences.edit().putLong(LAST_EVENT_ID, newestId).apply()
            return
        }

        val previousId = preferences.getLong(LAST_EVENT_ID, 0L)
        if (newestId < previousId) {
            // Mission reset starts event numbering over. Re-baseline so later
            // faults on the new mission can trigger notifications normally.
            preferences.edit().putLong(LAST_EVENT_ID, newestId).apply()
            return
        }
        if (newestId == previousId) return

        createChannels()

        if (canNotify()) {
            events.asSequence()
                .filter { it.id > previousId }
                .sortedBy { it.id }
                .filter(::shouldNotify)
                .toList()
                .takeLast(8) // Avoid a burst after a long offline catch-up.
                .forEach(::post)
        }

        // Even when permission is denied, keep notifications from replaying
        // every time Compose revisits this list or the app restarts.
        preferences.edit().putLong(LAST_EVENT_ID, newestId).apply()
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            !manager.areNotificationsEnabled()
        ) return false

        return true
    }

    private fun shouldNotify(event: MissionEvent): Boolean {
        if (event.kind == MissionEventKind.ALERT) return true
        if (event.severity == MissionEventSeverity.WARNING ||
            event.severity == MissionEventSeverity.CRITICAL
        ) return true

        // Important repair and servicing milestones are mission updates,
        // even if the event is logged at INFO severity.
        val title = event.title.lowercase()
        return title.contains("repair") ||
            title.contains("work order") ||
            title.contains("servicing mission")
    }

    private fun post(event: MissionEvent) {
        val fault = event.sourceKey == "MANUAL_FAULT" ||
            event.sourceKey == "POWER_STATUS" ||
            (event.kind == MissionEventKind.ALERT &&
                !MissionAlertRouting.isRepairRelated(event))

        val channelId = if (fault) FAULT_CHANNEL else MISSION_CHANNEL
        val targetPage = if (MissionAlertRouting.isRepairRelated(event)) 1 else 0

        // A new launcher Activity instance guarantees that the Compose route
        // receives the intended page even when the old Activity was singleTop.
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return
        launch.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_PAGE, targetPage)
            putExtra(EXTRA_SATELLITE, event.satelliteId)
        }

        val pending = PendingIntent.getActivity(
            app,
            event.id.hashCode(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(app, channelId)
        } else {
            Notification.Builder(app)
        }

        val heading = if (fault) "Satellite fault" else "Mission alert"
        val title = "$heading • ${event.satelliteId}: ${event.title}"

        val notification = notificationBuilder
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(event.description)
            .setStyle(Notification.BigTextStyle().bigText(event.description))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_view, "Inspect", pending)
            .build()

        manager.notify(event.id.hashCode(), notification)
    }
}
