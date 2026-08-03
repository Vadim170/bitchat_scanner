package com.vadim170.bitchatscanner

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

/**
 * Optional, non-ongoing detection notifications.  The cooldown ledger lives
 * in one bounded SharedPreferences set, so a receiver process recreation does
 * not turn every advertisement into a notification burst.
 */
internal class DetectionNotifier(context: Context) {
    companion object {
        const val CHANNEL_ID = "detections"
        private const val COOLDOWN_MS = 5 * 60 * 1000L
        private const val RETENTION_MS = 24 * 60 * 60 * 1000L
        private const val MAX_COOLDOWN_ENTRIES = 128
        private const val PREFS_NAME = "ble_scan_preferences"
        private const val KEY_NOTIFY_ENABLED = "notify_enabled"
        private const val KEY_COOLDOWN_LEDGER = "notification_cooldown_ledger"

        /** Visible to unit tests without constructing Android notifications. */
        internal fun cooldownMs(): Long = COOLDOWN_MS
    }

    private val appContext = context.applicationContext ?: context
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ledgerLock = Any()

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_NOTIFY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_NOTIFY_ENABLED, enabled).apply()
        if (enabled) ensureChannel()
    }

    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notifyDetection(address: String, name: String?, rssi: Int, timestampMs: Long) {
        if (address.isBlank() || address.equals("unknown", ignoreCase = true)) return
        // Check before claiming the cooldown so a denied/revoked permission
        // does not suppress the next notification after the user grants it.
        if (!isEnabled() || !canPostNotifications()) return
        if (!notificationEligible(
                enabled = true,
                permissionGranted = true,
                nowMs = timestampMs,
                lastNotifiedMs = readLastNotified(address, timestampMs),
                cooldownMs = COOLDOWN_MS,
            )
        ) return

        // Claim before posting so duplicate callbacks in the same process (and
        // a process restart immediately after this method) remain bounded.
        if (!claimCooldown(address, timestampMs)) return

        ensureChannel()

        val contentIntent = PendingIntent.getActivity(
            appContext,
            address.hashCode(),
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val displayName = name?.takeIf { it.isNotBlank() }
            ?: address.uppercase(Locale.US)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(appContext.getString(R.string.notification_detection_title))
            .setContentText(
                appContext.getString(
                    R.string.notification_detection_body,
                    displayName,
                    rssi,
                )
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // Keep the explicit API 33 permission check immediately before the
        // notify call; the permission can be revoked while this work runs.
        val posted = if (canPostNotifications()) {
            postNotification(address, notification)
        } else {
            false
        }
        if (!posted) rollbackCooldown(address, timestampMs)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(address: String, notification: android.app.Notification): Boolean {
        return try {
            NotificationManagerCompat.from(appContext)
                .notify(address, address.hashCode(), notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun ensureChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = appContext.getString(R.string.notification_channel_description)
            }
        )
    }

    private fun readLastNotified(address: String, nowMs: Long): Long? = synchronized(ledgerLock) {
        readLedger(nowMs)[address]
    }

    private fun claimCooldown(address: String, nowMs: Long): Boolean = synchronized(ledgerLock) {
        val ledger = readLedger(nowMs).toMutableMap()
        val previous = ledger[address]
        if (!notificationEligible(
                enabled = true,
                permissionGranted = true,
                nowMs = nowMs,
                lastNotifiedMs = previous,
                cooldownMs = COOLDOWN_MS,
            )
        ) return false

        ledger[address] = nowMs
        val bounded = ledger.entries
            .sortedByDescending { it.value }
            .take(MAX_COOLDOWN_ENTRIES)
            .associate { it.key to it.value }
        writeLedger(bounded)
        true
    }

    private fun rollbackCooldown(address: String, timestampMs: Long) {
        synchronized(ledgerLock) {
            val ledger = readLedger(timestampMs).toMutableMap()
            if (ledger[address] == timestampMs) {
                ledger.remove(address)
                writeLedger(ledger)
            }
        }
    }

    private fun writeLedger(ledger: Map<String, Long>) {
        val bounded = ledger.entries
            .sortedByDescending { it.value }
            .take(MAX_COOLDOWN_ENTRIES)
            .associate { it.key to it.value }
        val encoded = bounded.map { "${it.key}|${it.value}" }.toMutableSet()
        preferences.edit().putStringSet(KEY_COOLDOWN_LEDGER, encoded).commit()
    }

    private fun readLedger(nowMs: Long): Map<String, Long> {
        val oldestAllowed = nowMs - RETENTION_MS
        return preferences.getStringSet(KEY_COOLDOWN_LEDGER, emptySet())
            .orEmpty()
            .mapNotNull { entry ->
                val separator = entry.lastIndexOf('|')
                if (separator <= 0 || separator >= entry.length - 1) return@mapNotNull null
                val address = entry.substring(0, separator)
                val timestamp = entry.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
                if (timestamp < oldestAllowed || timestamp > nowMs) return@mapNotNull null
                address to timestamp
            }
            .toMap()
    }
}
