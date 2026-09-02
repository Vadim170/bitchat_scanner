package com.vadim170.bitchatscanner

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns both registrations used by the hybrid BLE scanner.
 *
 * A user-started session is persisted.  While the host activity is visible it
 * uses a low-latency callback; when the host stops, the callback is cancelled
 * before a filtered PendingIntent registration is installed.  The reverse
 * ordering is used on the next host start.  This class is process-wide so the
 * manifest receiver can hand results to the same database and notification
 * policy without introducing a service or a foreground notification.
 */
class BleScanCoordinator private constructor(context: Context) {

    companion object {
        private const val TAG = "BleScanCoordinator"
        private const val PREFS_NAME = "ble_scan_preferences"
        private const val KEY_SCAN_DESIRED = "scan_desired"
        private const val REQUEST_CODE = 0xB17C
        private const val MAX_RESULTS_PER_BROADCAST = 64

        @Volatile
        private var instance: BleScanCoordinator? = null

        fun getInstance(context: Context): BleScanCoordinator {
            return instance ?: synchronized(this) {
                instance ?: BleScanCoordinator(context.applicationContext).also { instance = it }
            }
        }
    }

    enum class State {
        STOPPED,
        ACTIVE_VISIBLE,
        PASSIVE_BACKGROUND,
        PERMISSION_REQUIRED,
        BLUETOOTH_DISABLED,
        LOCATION_DISABLED,
        UNAVAILABLE,
        ERROR,

        // Kept as source-compatible names for older integrations.  New code
        // must use ACTIVE_VISIBLE and STOPPED so passive mode is unambiguous.
        @Deprecated("Use STOPPED") IDLE,
        @Deprecated("Use ACTIVE_VISIBLE") SCANNING,
    }

    data class Event(val line: String)

    private val appContext = context.applicationContext ?: context
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(BluetoothManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val database = DetectionDbHelper(appContext)
    private val notifier = DetectionNotifier(appContext)
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val serviceUuid = ParcelUuid(BitchatBle.SERVICE_UUID)

    private val _state = MutableStateFlow(State.STOPPED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 128)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    @Volatile
    private var hostVisible = false

    @Volatile
    private var desiredScan = preferences.getBoolean(KEY_SCAN_DESIRED, false)

    @Volatile
    private var callbackRegistered = false

    @Volatile
    private var pendingIntentRegistered = false

    private var callbackScanner: BluetoothLeScanner? = null
    private var pendingIntentScanner: BluetoothLeScanner? = null
    private var pendingIntent: PendingIntent? = null

    private val callback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (hostVisible && callbackRegistered && _state.value == State.ACTIVE_VISIBLE) {
                recordResult(result, background = false)
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            if (hostVisible && callbackRegistered && _state.value == State.ACTIVE_VISIBLE) {
                results.forEach { recordResult(it, background = false) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            synchronized(this@BleScanCoordinator) {
                if (!callbackRegistered || _state.value != State.ACTIVE_VISIBLE) return
                callbackRegistered = false
                callbackScanner = null
                _state.value = State.ERROR
                emitLine("${timestamp()},scan_failed,$errorCode")
                Log.w(TAG, "BLE callback scan failed: errorCode=$errorCode")
            }
        }
    }

    /** Called by MainActivity.onStart. */
    @Synchronized
    fun onHostStarted() {
        hostVisible = true
        if (desiredScan) activateVisibleLocked()
    }

    /** Called by MainActivity.onResume after runtime permission changes. */
    @Synchronized
    fun onHostResumed() {
        if (hostVisible && desiredScan && _state.value != State.ACTIVE_VISIBLE) {
            activateVisibleLocked()
        }
    }

    /** Called by MainActivity.onStop. */
    @Synchronized
    fun onHostStopped() {
        hostVisible = false
        if (desiredScan) {
            activatePassiveLocked()
        } else {
            stopRegistrationsLocked()
            setStateLocked(State.STOPPED)
        }
    }

    /**
     * Persist a user Start intent, then choose the registration appropriate for
     * the current lifecycle.  A call while hidden still creates the passive
     * subscription, which makes the persisted intent useful after process
     * recreation as well.
     */
    @Synchronized
    fun startSession(): Boolean {
        desiredScan = true
        preferences.edit().putBoolean(KEY_SCAN_DESIRED, true).apply()
        return if (hostVisible) activateVisibleLocked() else activatePassiveLocked()
    }

    /** Persist a user Stop intent and cancel both registration forms. */
    @Synchronized
    fun stopSession() {
        val hadRegistration = callbackRegistered || pendingIntentRegistered ||
                _state.value != State.STOPPED
        desiredScan = false
        preferences.edit().putBoolean(KEY_SCAN_DESIRED, false).apply()
        stopRegistrationsLocked()
        setStateLocked(State.STOPPED)
        if (hadRegistration) emitLine("${timestamp()},scan_stopped")
    }

    fun isScanDesired(): Boolean = desiredScan

    fun notificationsEnabled(): Boolean = notifier.isEnabled()

    fun setNotificationsEnabled(enabled: Boolean) {
        notifier.setEnabled(enabled)
    }

    /**
     * Receiver entry point.  Work is handed to the coordinator's single
     * serialized single-thread executor; the receiver owns the goAsync PendingResult and passes
     * a finish callback so it can always release the broadcast promptly.
     */
    fun handlePendingIntent(intent: Intent, finish: () -> Unit) {
        val payload = extractPendingScanPayload(intent)
        try {
            io.execute {
                try {
                    processPendingPayload(payload)
                } finally {
                    finish()
                }
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to enqueue background scan result", e)
            finish()
        }
    }

    /** Package-visible seam for deterministic result tests. */
    internal fun acceptsResult(result: ScanResult): Boolean =
        matchesBitChatAdvertisement(result.scanRecord, serviceUuid)

    private fun processPendingPayload(payload: PendingScanPayload) {
        synchronized(this) {
            // A process can be started by a stale PendingIntent after the user
            // pressed Stop.  Likewise, onStart may have already reclaimed the
            // subscription for the foreground callback.
            if (!desiredScan || hostVisible) return
            if (_state.value != State.PASSIVE_BACKGROUND) {
                _state.value = State.PASSIVE_BACKGROUND
            }
        }

        payload.errorCode?.let {
            synchronized(this) {
                if (desiredScan && !hostVisible) _state.value = State.ERROR
            }
            emitLine("${timestamp()},background_scan_failed,$it")
            return
        }
        payload.results.take(MAX_RESULTS_PER_BROADCAST).forEach { result ->
            if (acceptsResult(result)) {
                // This task is already running under the receiver's executor;
                // persist before finish() so a process kill cannot discard a
                // result that was handed to goAsync().
                recordResult(result, background = true, persistImmediately = true)
            }
        }
    }

    @Synchronized
    private fun activateVisibleLocked(): Boolean {
        if (callbackRegistered && _state.value == State.ACTIVE_VISIBLE) return true

        // Stop the other registration before checking/starting the callback.
        stopPendingIntentLocked()
        val leScanner = scannerOrUpdateStateLocked() ?: return false
        return try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()

            // The visible callback is filtered like the PendingIntent path.
            // The platform suspends unfiltered scans when the screen turns off
            // and demotes them to opportunistic after its scan timeout (30
            // minutes by default), which would silently end a long visible
            // session.  The strict matcher in acceptsResult() still validates
            // every delivered result, so the filters only narrow delivery.
            startVisibleScanLocked(leScanner, settings)
            callbackScanner = leScanner
            callbackRegistered = true
            _state.value = State.ACTIVE_VISIBLE
            emitLine("${timestamp()},scan_started_visible")
            true
        } catch (e: SecurityException) {
            setStateLocked(State.PERMISSION_REQUIRED)
            emitLine("${timestamp()},no_scan_permission")
            Log.w(TAG, "BLE callback permission was revoked", e)
            false
        } catch (e: RuntimeException) {
            setStateLocked(State.ERROR)
            emitLine("${timestamp()},scan_start_failed")
            Log.e(TAG, "Unable to start visible BLE scan", e)
            false
        }
    }

    @Synchronized
    private fun activatePassiveLocked(): Boolean {
        if (pendingIntentRegistered && _state.value == State.PASSIVE_BACKGROUND) return true

        stopCallbackLocked()
        val leScanner = scannerOrUpdateStateLocked() ?: return false
        val pi = try {
            pendingIntentLocked()
        } catch (e: RuntimeException) {
            setStateLocked(State.ERROR)
            emitLine("${timestamp()},pending_intent_unavailable")
            Log.e(TAG, "Unable to create BLE PendingIntent", e)
            return false
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(3000L)
            .build()
        val filters = buildScanFilters()
        try {
            // Never fall back to an unfiltered PendingIntent: the platform
            // suspends unfiltered scans while the screen is off, and a hidden
            // app must not receive every advertisement nearby.
            startWithFilters(
                filters = filters,
                start = { leScanner.startScan(it, settings, pi) },
                cancel = { leScanner.stopScan(pi) },
                limitedEvent = "background_service_data_filter_limited",
            )
        } catch (e: SecurityException) {
            setStateLocked(State.PERMISSION_REQUIRED)
            emitLine("${timestamp()},no_scan_permission")
            Log.w(TAG, "BLE PendingIntent permission was revoked", e)
            return false
        } catch (e: IllegalArgumentException) {
            setStateLocked(State.ERROR)
            emitLine("${timestamp()},background_filter_rejected")
            Log.e(TAG, "BLE PendingIntent filters rejected", e)
            return false
        } catch (e: RuntimeException) {
            setStateLocked(State.ERROR)
            emitLine("${timestamp()},background_scan_start_failed")
            Log.e(TAG, "Unable to start BLE PendingIntent scan", e)
            return false
        }

        pendingIntentScanner = leScanner
        pendingIntentRegistered = true
        _state.value = State.PASSIVE_BACKGROUND
        emitLine("${timestamp()},scan_started_background")
        return true
    }

    private data class ScanFilters(
        val primary: List<ScanFilter>,
        val fallback: List<ScanFilter>,
    )

    /** Shared by the visible callback and the hidden PendingIntent registration. */
    private fun buildScanFilters(): ScanFilters {
        val serviceFilter = ScanFilter.Builder()
            .setServiceUuid(serviceUuid)
            .build()
        val fallback = mutableListOf(serviceFilter)

        val primary = mutableListOf(serviceFilter)
        try {
            // A zero-length pattern is the AOSP-defined way to match the
            // presence of service-data for this UUID without assuming payload
            // bytes.  Passing null would make ScanFilter.matches dereference a
            // null pattern on API26/AOSP, so it is deliberately not used.
            primary += ScanFilter.Builder()
                .setServiceData(serviceUuid, byteArrayOf(), byteArrayOf())
                .build()
        } catch (e: IllegalArgumentException) {
            emitLine("${timestamp()},service_data_filter_limited")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val solicitation = ScanFilter.Builder()
                    .setServiceSolicitationUuid(serviceUuid)
                    .build()
                primary += solicitation
                fallback += solicitation
            } catch (e: IllegalArgumentException) {
                emitLine("${timestamp()},solicitation_filter_limited")
            }
        } else {
            emitLine("${timestamp()},solicitation_filter_unavailable_api")
        }
        return ScanFilters(primary = primary, fallback = fallback)
    }

    /**
     * Start a registration with the primary filter list, retrying with the
     * documented service-UUID/solicitation fallback when a vendor stack rejects
     * the zero-length service-data presence filter.  An IllegalArgumentException
     * from the fallback propagates so each caller decides whether continuing
     * unfiltered is acceptable.
     */
    private fun startWithFilters(
        filters: ScanFilters,
        start: (List<ScanFilter>) -> Unit,
        cancel: () -> Unit,
        limitedEvent: String,
    ) {
        try {
            start(filters.primary)
            return
        } catch (e: IllegalArgumentException) {
            if (filters.fallback == filters.primary) throw e
            emitLine("${timestamp()},$limitedEvent")
            // Defensive cancellation in case a vendor accepted part of the
            // primary registration before rejecting its filter list.
            runCatching { cancel() }
        }
        start(filters.fallback)
    }

    /**
     * Register the visible callback with the shared filter list.  If a vendor
     * stack rejects even the documented fallback filters, the visible session
     * may continue unfiltered because the host is on screen; the PendingIntent
     * path never does that.
     *
     * Invoked only after scannerOrUpdateStateLocked() has passed the runtime
     * permission guard; the caller handles a SecurityException from a
     * concurrent revoke.
     */
    @SuppressLint("MissingPermission")
    private fun startVisibleScanLocked(leScanner: BluetoothLeScanner, settings: ScanSettings) {
        try {
            startWithFilters(
                filters = buildScanFilters(),
                start = { leScanner.startScan(it, settings, callback) },
                cancel = { leScanner.stopScan(callback) },
                limitedEvent = "visible_service_data_filter_limited",
            )
        } catch (e: IllegalArgumentException) {
            emitLine("${timestamp()},visible_filters_rejected")
            Log.w(TAG, "Visible BLE filters rejected; continuing unfiltered while on screen", e)
            runCatching { leScanner.stopScan(callback) }
            leScanner.startScan(null, settings, callback)
        }
    }

    @Synchronized
    private fun stopRegistrationsLocked() {
        // Keep this order explicit: callback first, then PendingIntent.  It is
        // also safe when either registration was already cleared by a failure.
        stopCallbackLocked()
        stopPendingIntentLocked()
    }

    private fun stopCallbackLocked() {
        val currentScanner = callbackScanner
        callbackRegistered = false
        callbackScanner = null
        if (currentScanner != null && hasScanPermission()) stopCallbackRegistration(currentScanner)
    }

    private fun stopPendingIntentLocked() {
        val currentScanner = pendingIntentScanner
        pendingIntentRegistered = false
        pendingIntentScanner = null
        if (!hasScanPermission()) {
            pendingIntent = null
            return
        }
        val scannerForStop = currentScanner ?: runCatching {
            bluetoothManager?.adapter?.bluetoothLeScanner
        }.getOrNull()
        if (scannerForStop == null) {
            pendingIntent = null
            return
        }
        // Recreate the same explicit PendingIntent identity after a process
        // restart.  BluetoothLeScanner.stopScan(PendingIntent) matches the
        // operation (package/action/request code), not this process's object
        // reference; returning early when the field is null would leave the
        // old registration alive while a callback starts.
        val pi = try {
            pendingIntentLocked()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to recreate background BLE PendingIntent", e)
            return
        }
        try {
            // Use the same stable PendingIntent object.  FLAG_CANCEL_CURRENT
            // would make BluetoothLeScanner.stopScan unable to find it.
            stopPendingIntentRegistration(scannerForStop, pi)
        } finally {
            pendingIntent = null
        }
    }

    /** Invoked only after hasScanPermission() has passed the runtime guard. */
    @SuppressLint("MissingPermission")
    private fun stopCallbackRegistration(scanner: BluetoothLeScanner) {
        try {
            scanner.stopScan(callback)
        } catch (e: SecurityException) {
            Log.w(TAG, "BLE scan permission was revoked while stopping callback", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to stop visible BLE scan", e)
        }
    }

    /** Invoked only after hasScanPermission() has passed the runtime guard. */
    @SuppressLint("MissingPermission")
    private fun stopPendingIntentRegistration(
        scanner: BluetoothLeScanner,
        pi: PendingIntent,
    ) {
        try {
            scanner.stopScan(pi)
        } catch (e: SecurityException) {
            Log.w(TAG, "BLE scan permission was revoked while stopping PendingIntent", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to stop background BLE scan", e)
        }
    }

    private fun pendingIntentLocked(): PendingIntent {
        pendingIntent?.let { return it }
        val intent = Intent(appContext, BleScanReceiver::class.java)
            .setPackage(appContext.packageName)
        return PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        ).also { pendingIntent = it }
    }

    private fun recordResult(
        result: ScanResult,
        background: Boolean,
        persistImmediately: Boolean = false,
    ) {
        if (!acceptsResult(result)) return
        synchronized(this) {
            if (!desiredScan) return
            if (background) {
                if (hostVisible) return
            } else if (!hostVisible || !callbackRegistered || _state.value != State.ACTIVE_VISIBLE) {
                return
            }
        }

        // A PendingIntent can outlive a runtime permission grant. Re-check
        // CONNECT before touching BluetoothDevice metadata; a revoke must not
        // crash the receiver or foreground callback.
        if (!hasConnectPermission()) {
            markPermissionRequired()
            return
        }

        val nowMs = System.currentTimeMillis()
        val record = result.scanRecord ?: return
        val metadata = try {
            readDeviceMetadata(result, record)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read BLE device metadata", e)
            markPermissionRequired()
            return
        }
        val address = metadata.address
        val name = metadata.name
        val serviceDataHex = record.serviceData?.get(serviceUuid)
            ?.joinToString("") { "%02X".format(Locale.US, it.toInt() and 0xFF) }
        // A process running the receiver must never turn a background wake-up
        // into a fresh location request.  Only the visible callback path reads
        // a cached last-known location.
        val location = if (!background) {
            synchronized(this) {
                if (hostVisible && callbackRegistered) getBestLastKnownLocation() else null
            }
        } else {
            null
        }
        val line = buildDetectionLine(
            timestampMs = nowMs,
            address = address,
            name = name,
            rssi = result.rssi,
            location = location,
            serviceDataHex = serviceDataHex,
            background = background,
        )
        val row = DetectionRow(
            timestamp = nowMs,
            address = address,
            name = name,
            rssi = result.rssi,
            lat = location?.latitude,
            lon = location?.longitude,
            accuracy = location?.accuracy,
            provider = location?.provider,
            serviceDataHex = serviceDataHex,
        )

        val persist: () -> Unit = persist@{
            try {
                val allowed = synchronized(this) {
                    desiredScan &&
                            (background && !hostVisible ||
                                    !background && hostVisible && callbackRegistered)
                }
                if (!allowed) return@persist
                database.insertAndPrune(row)
                emitLine(line)
                notifier.notifyDetection(address, name, result.rssi, nowMs)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving detection to DB: $address", e)
            }
        }
        if (persistImmediately) {
            persist()
        } else {
            try {
                io.execute { persist() }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Unable to enqueue detection", e)
            }
        }
    }

    private data class DeviceMetadata(val address: String, val name: String)

    /** Called only after an explicit BLUETOOTH_CONNECT check in recordResult. */
    @SuppressLint("MissingPermission")
    private fun readDeviceMetadata(
        result: ScanResult,
        record: android.bluetooth.le.ScanRecord,
    ): DeviceMetadata {
        val device = result.device
        return DeviceMetadata(
            address = device.address,
            name = device.name ?: record.deviceName ?: "",
        )
    }

    private fun markPermissionRequired() {
        val shouldEmit = synchronized(this) {
            if (!desiredScan) return@synchronized false
            val changed = _state.value != State.PERMISSION_REQUIRED
            _state.value = State.PERMISSION_REQUIRED
            changed
        }
        if (shouldEmit) emitLine("${timestamp()},no_scan_permission")
    }

    private fun buildDetectionLine(
        timestampMs: Long,
        address: String,
        name: String,
        rssi: Int,
        location: Location?,
        serviceDataHex: String?,
        background: Boolean,
    ): String = buildString {
        append(formatTimestamp(timestampMs))
        append(",")
        append(address)
        append(", RSSI ")
        append(rssi)
        if (name.isNotEmpty()) {
            append(", ")
            append(name)
        }
        if (background) append(", mode=background")
        if (location != null) {
            append(", lat=")
            append(location.latitude)
            append(", lon=")
            append(location.longitude)
        }
        if (!serviceDataHex.isNullOrEmpty()) {
            append(", sd=")
            append(serviceDataHex.take(16))
            append("…")
        }
    }

    private fun getBestLastKnownLocation(): Location? {
        if (!hasPreciseLocationPermission()) return null

        return try {
            val locationManager =
                appContext.getSystemService(LocationManager::class.java) ?: return null
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
            var best: Location? = null
            for (provider in providers) {
                val location = locationManager.getLastKnownLocation(provider) ?: continue
                if (best == null || location.time > best!!.time) best = location
            }
            best
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to read last known location", e)
            null
        }
    }

    private fun scannerOrUpdateStateLocked(): BluetoothLeScanner? {
        if (!hasRequiredPermissions()) {
            setStateLocked(State.PERMISSION_REQUIRED)
            emitLine("${timestamp()},no_scan_permission")
            return null
        }
        if (!isLocationEnabled()) {
            setStateLocked(State.LOCATION_DISABLED)
            emitLine("${timestamp()},location_disabled")
            return null
        }
        val manager = bluetoothManager
        if (manager == null) {
            setStateLocked(State.UNAVAILABLE)
            emitLine("${timestamp()},bluetooth_not_available")
            return null
        }
        val adapter = try {
            manager.adapter
        } catch (e: SecurityException) {
            setStateLocked(State.PERMISSION_REQUIRED)
            emitLine("${timestamp()},no_scan_permission")
            return null
        }
        if (adapter == null) {
            setStateLocked(State.UNAVAILABLE)
            emitLine("${timestamp()},bluetooth_not_available")
            return null
        }
        val enabled = try {
            adapter.isEnabled
        } catch (e: SecurityException) {
            setStateLocked(State.PERMISSION_REQUIRED)
            emitLine("${timestamp()},no_scan_permission")
            return null
        }
        if (!enabled) {
            setStateLocked(State.BLUETOOTH_DISABLED)
            emitLine("${timestamp()},bluetooth_disabled")
            return null
        }
        return try {
            adapter.bluetoothLeScanner ?: run {
                setStateLocked(State.UNAVAILABLE)
                emitLine("${timestamp()},scanner_not_available")
                null
            }
        } catch (e: SecurityException) {
            setStateLocked(State.PERMISSION_REQUIRED)
            emitLine("${timestamp()},no_scan_permission")
            null
        }
    }

    /**
     * Precise location is part of the scan contract on every supported API
     * level.  The manifest declares BLUETOOTH_SCAN without the location
     * opt-out flag, so on Android 12+ the Bluetooth stack only delivers results while
     * ACCESS_FINE_LOCATION is granted; on Android 8-11 the location grant is
     * the classic BLE scan requirement.
     */
    private fun hasRequiredPermissions(): Boolean =
        hasScanPermission() && hasConnectPermission() && hasPreciseLocationPermission()

    private fun hasPreciseLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * The Bluetooth stack drops scan results for a location-attributed app
     * while Location Services are off and reports no error.  Surface that as
     * a distinct state instead of an empty "active" scan.
     */
    private fun isLocationEnabled(): Boolean {
        val locationManager =
            appContext.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            val enabled = Settings.Secure.getInt(
                appContext.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF,
            ) != Settings.Secure.LOCATION_MODE_OFF
            enabled
        }
    }

    private fun hasScanPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            appContext,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_SCAN
            } else {
                Manifest.permission.BLUETOOTH_ADMIN
            },
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ActivityCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED

    private fun setStateLocked(state: State) {
        _state.value = state
    }

    private fun timestamp(): String = formatTimestamp(System.currentTimeMillis())

    private fun formatTimestamp(timeMs: Long): String = synchronized(dateFormat) {
        dateFormat.format(Date(timeMs))
    }

    private fun emitLine(line: String) {
        _events.tryEmit(Event(line))
        if (line.contains("scan_failed") || line.contains("bluetooth_disabled") ||
            line.contains("location_disabled") || line.contains("filters_rejected") ||
            line.contains("no_scan_permission") || line.contains("scan_started") ||
            line.contains("scan_stopped") || line.contains("filter_limited") ||
            line.contains("filter_unavailable")
        ) {
            Log.i(TAG, line)
        }
    }
}
