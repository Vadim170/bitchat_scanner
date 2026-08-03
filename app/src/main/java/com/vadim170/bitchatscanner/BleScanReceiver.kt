package com.vadim170.bitchatscanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives only the explicit BluetoothLeScanner PendingIntent registration.
 * The receiver is non-exported in the manifest and does no blocking work on
 * the broadcast thread.
 */
class BleScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        BleScanCoordinator.getInstance(context).handlePendingIntent(intent) {
            pendingResult.finish()
        }
    }
}
