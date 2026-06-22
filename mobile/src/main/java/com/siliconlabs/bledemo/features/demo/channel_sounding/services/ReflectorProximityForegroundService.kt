package com.siliconlabs.bledemo.features.demo.channel_sounding.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.DeadObjectException
import android.os.RemoteException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
import com.siliconlabs.bledemo.features.demo.channel_sounding.activities.ReflectorDashboardActivity
import com.siliconlabs.bledemo.features.demo.channel_sounding.gatt.DigitalKeyGattClient
import com.siliconlabs.bledemo.features.demo.channel_sounding.gatt.DigitalKeyGattResolver
import com.siliconlabs.bledemo.features.demo.channel_sounding.gatt.DigitalKeyGattSubscription
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Foreground service (`connectedDevice`) for Reflector digital-key monitoring.
 *
 * Connection policy (matches Android “Communicate in the background” — Connect to a device):
 * https://developer.android.com/develop/connectivity/bluetooth/ble/background#connect-device
 *
 * 1. **First connection** after pairing: [BluetoothDevice.connectGatt] with **autoConnect = false**
 *    for a faster direct connection.
 * 2. **After the first link loss**: close that client and open a new one with **autoConnect = true**
 *    so the stack reconnects when the peripheral is back in range. Further disconnects are not
 *    handled by closing the client (would cancel auto-reconnect).
 *
 * Lock/unlock follows GATT only ([DigitalKeyGattClient] on 0xBBCC / 0xAABB); RSSI is not used for lock UI.
 *
 * Does not call [BluetoothGatt.disconnect]. [close] is only used when switching devices or in
 * [onDestroy].
 */
@SuppressLint("MissingPermission")
class ReflectorProximityForegroundService : Service() {

    private val binder = LocalBinder()
    private val listeners = CopyOnWriteArrayList<ReflectorMonitoringListener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var deviceAddress: String? = null
    private var deviceName: String? = null
    private var bluetoothGatt: BluetoothGatt? = null

    /** After first disconnect we use autoConnect=true and must not replace the client from UI restarts. */
    private var stackManagingReconnect: Boolean = false

    /** Prevents overlapping handleDeadGatt → connectOrRefresh loops ("Too many transaction errors"). */
    private var gattRecoveryInProgress: Boolean = false

    private var lockState: ReflectorDigitalKeyLockState = ReflectorDigitalKeyLockState.UNKNOWN
    private var digitalKeyPhase: DigitalKeyConnectionPhase = DigitalKeyConnectionPhase.DISCONNECTED
    private var phaseDetail: String? = null

    private var pendingRediscoverRunnable: Runnable? = null
    private var discoverRetryCount: Int = 0
    private var lastResolved: DigitalKeyGattResolver.Resolved? = null

    private var bondReceiverRegistered: Boolean = false
    private var aclReceiverRegistered: Boolean = false

    private var reflectorCsEnabled: Boolean = true

    /** True after pipeline reaches LIVE at least once. */
    private var sessionReachedLive: Boolean = false

    private var rangeLossOutageActive: Boolean = false
    private var toastDisconnectedShown: Boolean = false
    private var toastReconnectingShown: Boolean = false

    private var aclReconnectRunnable: Runnable? = null

    private var sessionLostNotified: Boolean = false
    private var sessionRestoreTimeoutRunnable: Runnable? = null

    private val digitalKeySubscription = DigitalKeyGattSubscription(
        logTag = TAG,
        mainHandler = mainHandler,
        listener = object : DigitalKeyGattSubscription.Listener {
            override fun onSubscribed(gatt: BluetoothGatt) {
                val restoringFromRangeLoss = rangeLossOutageActive
                setPhase(DigitalKeyConnectionPhase.STARTING_RANGING, null)
                listeners.forEach { it.onDigitalKeyNotifySubscribed(true) }
                startCsReflectorIfPossible(gatt.device)
                val csActive = csRangingController.isRunning()
                listeners.forEach { it.onCsReflectorActive(csActive) }
                sessionReachedLive = true
                setPhase(
                    DigitalKeyConnectionPhase.LIVE,
                    if (csActive) null else getString(R.string.reflector_dashboard_cs_reflector_inactive)
                )
                if (restoringFromRangeLoss) {
                    notifySessionRestored()
                }
            }

            override fun onSubscribeFailed(reason: String, recoverable: Boolean) {
                Timber.tag(TAG).w("DigitalKey subscribe failed: %s recoverable=%s", reason, recoverable)
                val needsRePair = reason.contains("re-pair", ignoreCase = true)
                if (sessionReachedLive && (!recoverable || needsRePair)) {
                    notifySessionLost("subscribeFailed:$reason")
                    return
                }
                if (recoverable && discoverRetryCount < MAX_DISCOVER_RETRIES) {
                    setPhase(DigitalKeyConnectionPhase.DISCOVERING, reason)
                    val g = bluetoothGatt
                    if (g != null && isGattConnected()) {
                        scheduleDiscoverRetry(g, "subscribeFail")
                        return
                    }
                }
                setPhase(DigitalKeyConnectionPhase.SETUP_FAILED, reason)
                listeners.forEach {
                    it.onDigitalKeyNotifySubscribed(false)
                    it.onDigitalKeyGattUnavailable(recoverable, reason)
                }
            }

            override fun onLockState(state: ReflectorDigitalKeyLockState, source: String) {
                applyLockState(state, source)
            }
        }
    )

    private val csRangingController = ReflectorCsRangingController(this, mainHandler) { active ->
        listeners.forEach { it.onCsReflectorActive(active) }
    }

    /** Serializes session work with disconnect handling (both run on main looper). */
    private val connectOrRefreshRunnable = Runnable { connectOrRefreshOnMain() }

    private val aclStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val addr = deviceAddress ?: return
            if (!addr.equals(device.address, ignoreCase = true)) return
            if (!stackManagingReconnect) return
            mainHandler.post { scheduleConnectOrRefreshDebouncedFromAcl() }
        }
    }

    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val addr = deviceAddress ?: return
            if (!addr.equals(device.address, ignoreCase = true)) return
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            mainHandler.post { onBondStateChanged(bondState) }
        }
    }

    private fun cancelPendingDigitalKeyGattWork() {
        digitalKeySubscription.cancelPending()
        pendingRediscoverRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRediscoverRunnable = null
    }

    private fun registerBondStateReceiver() {
        if (bondReceiverRegistered) return
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bondStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(bondStateReceiver, filter)
        }
        bondReceiverRegistered = true
    }

    private fun unregisterBondStateReceiver() {
        if (!bondReceiverRegistered) return
        try {
            unregisterReceiver(bondStateReceiver)
        } catch (_: Exception) { }
        bondReceiverRegistered = false
    }

    private fun onBondStateChanged(bondState: Int) {
        when (bondState) {
            BluetoothDevice.BOND_BONDED -> {
                if (sessionLostNotified) return
                // After LIVE, a new bond event means the stack is re-pairing (e.g. board reset) — do not
                // refresh GATT or continue the Digital Key pipeline.
                if (sessionReachedLive) {
                    Timber.tag(TAG).w("BOND_BONDED after LIVE; ending session (no re-bond)")
                    notifySessionLost("bondedAfterLive")
                    return
                }
                Timber.tag(TAG).i("Bond complete; refreshing Digital Key GATT if connected")
                val g = bluetoothGatt ?: return
                if (!isGattConnected()) return
                digitalKeySubscription.reset()
                discoverRetryCount = 0
                lastResolved = null
                setPhase(DigitalKeyConnectionPhase.DISCOVERING, null)
                safeDiscoverServices(g, "afterBond")
            }
            BluetoothDevice.BOND_BONDING -> {
                if (sessionReachedLive && !sessionLostNotified) {
                    Timber.tag(TAG).w("BOND_BONDING after LIVE; ending session (no re-bond)")
                    notifySessionLost("bondingAfterLive")
                    return
                }
                if (sessionLostNotified) return
                setPhase(DigitalKeyConnectionPhase.BONDING, null)
            }
            BluetoothDevice.BOND_NONE -> {
                if (sessionReachedLive) {
                    Timber.tag(TAG).w("BOND_NONE after LIVE; ending reflector session")
                    notifySessionLost("bondRemoved")
                    return
                }
                digitalKeySubscription.reset()
                csRangingController.stop()
            }
        }
    }

    /**
     * The BluetoothGatt binder can die while [isGattConnected] still reports connected (e.g. after
     * [BluetoothGatt.close] or stack teardown). Recover by opening a fresh GATT client.
     */
    private fun handleDeadGatt(operation: String) {
        if (sessionLostNotified) return
        if (gattRecoveryInProgress) {
            Timber.tag(TAG).d("GATT recovery already in progress; skip (%s)", operation)
            return
        }
        gattRecoveryInProgress = true
        Timber.tag(TAG).w("GATT client dead during %s; reopening connection", operation)
        cancelPendingDigitalKeyGattWork()
        digitalKeySubscription.reset()
        csRangingController.stop()
        lastResolved = null
        try {
            bluetoothGatt?.close()
        } catch (_: Exception) { }
        bluetoothGatt = null
        if (sessionReachedLive) {
            stackManagingReconnect = true
        }
        setReconnectingPhaseIfNeeded()
        scheduleConnectOrRefresh()
    }

    /**
     * [isGattConnected] can stay true briefly while the Binder is already dead (e.g. after
     * [BluetoothGatt.close] from pairing UI). Probe the client before any GATT transaction.
     */
    private fun isGattClientUsable(gatt: BluetoothGatt): Boolean {
        if (gatt !== bluetoothGatt) return false
        if (!isGattConnected()) return false
        return try {
            @Suppress("UNUSED_VARIABLE")
            gatt.device.address
            @Suppress("UNUSED_VARIABLE")
            gatt.services
            true
        } catch (e: DeadObjectException) {
            false
        } catch (e: RemoteException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun safeDiscoverServices(gatt: BluetoothGatt, reason: String): Boolean {
        if (gatt !== bluetoothGatt) return false
        if (!isGattClientUsable(gatt)) {
            handleDeadGatt("discoverServices:unusable:$reason")
            return false
        }
        return try {
            val queued = gatt.discoverServices()
            if (!queued) {
                Timber.tag(TAG).w("discoverServices(%s) returned false; recovering GATT", reason)
                handleDeadGatt("discoverServices:returnedFalse:$reason")
            }
            queued
        } catch (e: DeadObjectException) {
            handleDeadGatt("discoverServices:$reason")
            false
        } catch (e: RemoteException) {
            handleDeadGatt("discoverServices:$reason")
            false
        } catch (e: IllegalStateException) {
            handleDeadGatt("discoverServices:$reason")
            false
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "discoverServices(%s) failed", reason)
            handleDeadGatt("discoverServices:$reason")
            false
        }
    }

    fun setReflectorCsEnabled(enabled: Boolean) {
        reflectorCsEnabled = enabled
        csRangingController.setEnabled(enabled)
        if (!enabled) {
            csRangingController.stop()
        } else {
            val g = bluetoothGatt
            if (g != null && isGattConnected() && digitalKeySubscription.isSubscribed()) {
                startCsReflectorIfPossible(g.device)
            }
        }
    }

    fun isReflectorCsEnabled(): Boolean = reflectorCsEnabled

    fun isDigitalKeyNotifySubscribed(): Boolean = digitalKeySubscription.isSubscribed()

    fun isCsRangingActive(): Boolean = csRangingController.isRunning()

    /** True after [notifySessionLost] (bond removed, board reset, or other terminal session loss). */
    fun isReflectorSessionLost(): Boolean = sessionLostNotified

    fun getDigitalKeyPhase(): DigitalKeyConnectionPhase = digitalKeyPhase

    /** Same lock byte the foreground notification uses for padlock art. */
    fun getLockState(): ReflectorDigitalKeyLockState = lockState

    /** Matches [buildNotification] “unlocked” presentation: GATT up and BBCC reports 0x00. */
    fun isDigitalKeyUnlockedForUi(): Boolean =
        isGattConnected() && lockState == ReflectorDigitalKeyLockState.UNLOCKED

    /** User-initiated recovery when BBCC was not exposed or CCCD failed. */
    fun retryDigitalKeySetup() {
        mainHandler.post {
            val g = bluetoothGatt ?: return@post
            if (!isGattConnected()) {
                scheduleConnectOrRefresh()
                return@post
            }
            discoverRetryCount = 0
            digitalKeySubscription.reset()
            lastResolved = null
            setPhase(DigitalKeyConnectionPhase.DISCOVERING, getString(R.string.reflector_dashboard_retrying_gatt))
            safeDiscoverServices(g, "userRetry")
        }
    }

    private fun setPhase(phase: DigitalKeyConnectionPhase, detail: String?) {
        if (digitalKeyPhase == phase && phaseDetail == detail) return
        digitalKeyPhase = phase
        phaseDetail = detail
        listeners.forEach { it.onDigitalKeyPhaseChanged(phase, detail) }
    }

    private fun setReconnectingPhaseIfNeeded() {
        if (deviceAddress.isNullOrBlank()) return
        if (digitalKeyPhase != DigitalKeyConnectionPhase.RECONNECTING) {
            notifyReconnecting()
        }
        setPhase(
            DigitalKeyConnectionPhase.RECONNECTING,
            getString(R.string.reflector_dashboard_awaiting_auto_reconnect)
        )
    }

    private fun notifyRangeLossDisconnected() {
        if (!sessionReachedLive) return
        // Toasts are shown by ReflectorDashboardActivity (foreground-safe). The service keeps the
        // outage state only to drive the foreground notification text.
        rangeLossOutageActive = true
        toastReconnectingShown = false
        if (!toastDisconnectedShown) {
            toastDisconnectedShown = true
        }
    }

    private fun notifyReconnecting() {
        if (!rangeLossOutageActive || toastReconnectingShown) return
        toastReconnectingShown = true
    }

    private fun notifySessionRestored() {
        rangeLossOutageActive = false
        toastDisconnectedShown = false
        toastReconnectingShown = false
        cancelSessionRestoreTimeout()
    }

    private fun scheduleSessionRestoreTimeout() {
        if (!sessionReachedLive || sessionLostNotified) return
        cancelSessionRestoreTimeout()
        val r = Runnable {
            sessionRestoreTimeoutRunnable = null
            if (sessionLostNotified || !sessionReachedLive) return@Runnable
            if (digitalKeyPhase == DigitalKeyConnectionPhase.LIVE && isGattConnected()) return@Runnable
            Timber.tag(TAG).w("Session restore timeout; treating as board reset / session loss")
            notifySessionLost("restoreTimeout")
        }
        sessionRestoreTimeoutRunnable = r
        mainHandler.postDelayed(r, SESSION_RESTORE_TIMEOUT_MS)
    }

    private fun cancelSessionRestoreTimeout() {
        sessionRestoreTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        sessionRestoreTimeoutRunnable = null
    }

    private fun notifySessionLost(reason: String) {
        if (sessionLostNotified) return
        sessionLostNotified = true
        Timber.tag(TAG).e("Reflector session lost: %s", reason)
        cancelSessionRestoreTimeout()
        cancelPendingDigitalKeyGattWork()
        stackManagingReconnect = false
        rangeLossOutageActive = false
        digitalKeySubscription.reset()
        csRangingController.stop()
        lastResolved = null
        try {
            bluetoothGatt?.close()
        } catch (_: Exception) { }
        bluetoothGatt = null
        sessionReachedLive = false
        setPhase(DigitalKeyConnectionPhase.DISCONNECTED, null)
        applyLockState(ReflectorDigitalKeyLockState.UNKNOWN, "sessionLost")
        listeners.forEach { it.onReflectorSessionLost() }
    }

    private fun scheduleConnectOrRefreshDebouncedFromAcl() {
        aclReconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable {
            aclReconnectRunnable = null
            if (!stackManagingReconnect) return@Runnable
            Timber.tag(TAG).i("ACL_CONNECTED for target; nudging GATT reconnect")
            connectOrRefreshOnMain()
        }
        aclReconnectRunnable = r
        mainHandler.postDelayed(r, 300L)
    }

    private fun registerAclStateReceiver() {
        if (aclReceiverRegistered) return
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(aclStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(aclStateReceiver, filter)
        }
        aclReceiverRegistered = true
    }

    private fun unregisterAclStateReceiver() {
        if (!aclReceiverRegistered) return
        try {
            unregisterReceiver(aclStateReceiver)
        } catch (_: Exception) { }
        aclReceiverRegistered = false
        aclReconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        aclReconnectRunnable = null
    }

    private fun startCsReflectorIfPossible(device: BluetoothDevice) {
        if (!reflectorCsEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            csRangingController.startIfPossible(device)
        }
    }

    private val gattCallback = object : TimeoutGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            mainHandler.post {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        if (gatt != bluetoothGatt) return@post
                        gattRecoveryInProgress = false
                        cancelPendingDigitalKeyGattWork()
                        digitalKeySubscription.reset()
                        csRangingController.stop()
                        discoverRetryCount = 0
                        lastResolved = null
                        applyLockState(ReflectorDigitalKeyLockState.UNKNOWN, "connected")
                        notifyConnection(true)
                        setPhase(DigitalKeyConnectionPhase.DISCOVERING, null)
                        updateNotificationThrottled()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        }
                        @Suppress("DEPRECATION")
                        gatt.requestMtu(247)
                        safeDiscoverServices(gatt, "onConnected")
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        val addr = deviceAddress
                        if (addr.isNullOrBlank() || !addr.equals(gatt.device.address, ignoreCase = true)) {
                            return@post
                        }
                        if (sessionLostNotified) {
                            return@post
                        }
                        if (sessionReachedLive) {
                            if (gatt.device.bondState == BluetoothDevice.BOND_BONDING) {
                                Timber.tag(TAG).w(
                                    "GATT disconnect while bonding after LIVE; ending session",
                                )
                                notifySessionLost("disconnectWhileBonding")
                                return@post
                            }
                            if (isGattStaleBondDisconnectStatus(status)) {
                                Timber.tag(TAG).w(
                                    "GATT disconnect status=%s after LIVE; ending session (stale bond)",
                                    status,
                                )
                                notifySessionLost("disconnectStaleBond:$status")
                                return@post
                            }
                        }
                        cancelPendingDigitalKeyGattWork()
                        digitalKeySubscription.reset()
                        csRangingController.stop()
                        discoverRetryCount = 0
                        lastResolved = null
                        applyLockState(ReflectorDigitalKeyLockState.UNKNOWN, "disconnected")
                        notifyRangeLossDisconnected()
                        scheduleSessionRestoreTimeout()
                        notifyConnection(false)
                        listeners.forEach { it.onDigitalKeyNotifySubscribed(false) }
                        if (!stackManagingReconnect) {
                            stackManagingReconnect = true
                            try {
                                gatt.close()
                            } catch (_: Exception) { }
                            if (bluetoothGatt === gatt) {
                                bluetoothGatt = null
                            }
                            openGattClient(autoConnect = true)
                            Timber.tag(TAG).i("Switched to autoConnect=true after first disconnect")
                        } else if (bluetoothGatt == null) {
                            openGattClient(autoConnect = true)
                            Timber.tag(TAG).i("Opened autoConnect GATT after repeat link loss")
                        }
                        setReconnectingPhaseIfNeeded()
                        updateNotificationThrottled()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            mainHandler.post {
                if (gatt != bluetoothGatt) return@post
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    handleServicesDiscoveredSuccess(gatt)
                } else {
                    setPhase(DigitalKeyConnectionPhase.SETUP_FAILED, "GATT discovery status $status")
                    if (discoverRetryCount < MAX_DISCOVER_RETRIES) {
                        scheduleDiscoverRetry(gatt, "status$status")
                    } else {
                        listeners.forEach { it.onServiceDiscoveryFailed() }
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.tag(TAG).i("MTU negotiated: %d", mtu)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            mainHandler.post {
                if (gatt != bluetoothGatt) return@post
                digitalKeySubscription.onDescriptorWrite(gatt, descriptor, status)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            mainHandler.post {
                if (gatt != bluetoothGatt) return@post
                digitalKeySubscription.onCharacteristicRead(
                    gatt, characteristic, characteristic.value, status
                )
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mainHandler.post {
                    if (gatt != bluetoothGatt) return@post
                    digitalKeySubscription.onCharacteristicRead(gatt, characteristic, value, status)
                }
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            mainHandler.post {
                digitalKeySubscription.onCharacteristicChanged(characteristic, characteristic.value)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mainHandler.post {
                    digitalKeySubscription.onCharacteristicChanged(characteristic, value)
                }
            }
        }

        override fun onTimeout() {
            mainHandler.post {
                listeners.forEach { it.onServiceDiscoveryFailed() }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ReflectorProximityForegroundService = this@ReflectorProximityForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerBondStateReceiver()
        registerAclStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfSafely()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val addr = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                val name = intent.getStringExtra(EXTRA_DEVICE_NAME)
                if (addr.isNullOrBlank()) {
                    Timber.tag(TAG).w("ACTION_START without address")
                    stopSelfSafely()
                    return START_REDELIVER_INTENT
                }
                val addressChanged = !addr.equals(deviceAddress, ignoreCase = true)
                val forceNewSession = intent.getBooleanExtra(EXTRA_FORCE_NEW_SESSION, false)
                deviceAddress = addr
                deviceName = name
                if (addressChanged || forceNewSession) {
                    stackManagingReconnect = false
                    sessionReachedLive = false
                    sessionLostNotified = false
                    rangeLossOutageActive = false
                    toastDisconnectedShown = false
                    toastReconnectingShown = false
                    cancelSessionRestoreTimeout()
                    releaseGatt()
                }
                startAsForegroundWithType()
                scheduleConnectOrRefresh()
            }
            else -> {
                if (!deviceAddress.isNullOrBlank()) {
                    startAsForegroundWithType()
                    scheduleConnectOrRefresh()
                } else {
                    Timber.tag(TAG).w("onStartCommand with no session")
                    stopSelfSafely()
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        unregisterBondStateReceiver()
        unregisterAclStateReceiver()
        cancelSessionRestoreTimeout()
        cancelPendingDigitalKeyGattWork()
        mainHandler.removeCallbacks(connectOrRefreshRunnable)
        releaseGatt()
        cachedLargeIconLocked?.recycle()
        cachedLargeIconLocked = null
        cachedLargeIconUnlocked?.recycle()
        cachedLargeIconUnlocked = null
        super.onDestroy()
    }

    fun addListener(listener: ReflectorMonitoringListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
        mainHandler.post { pushSnapshotTo(listener) }
    }

    fun removeListener(listener: ReflectorMonitoringListener) {
        listeners.remove(listener)
    }

    fun getDeviceAddress(): String? = deviceAddress
    fun getDeviceName(): String? = deviceName

    fun isGattConnected(): Boolean {
        val g = bluetoothGatt ?: return false
        val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        return try {
            mgr.getConnectionState(g.device, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED
        } catch (_: Exception) {
            false
        }
    }

    private fun pushSnapshotTo(listener: ReflectorMonitoringListener) {
        val connected = isGattConnected()
        listener.onConnectionUiState(connected)
        val g = bluetoothGatt
        if (connected && g != null) {
            try {
                val services = g.services
                if (services.isNotEmpty()) {
                    listener.onServicesDiscovered(services)
                }
            } catch (_: Exception) { }
        }
        listener.onLockStateChanged(lockState)
        listener.onDigitalKeyPhaseChanged(digitalKeyPhase, phaseDetail)
        listener.onDigitalKeyNotifySubscribed(digitalKeySubscription.isSubscribed())
        listener.onCsReflectorActive(csRangingController.isRunning())
    }

    private fun handleServicesDiscoveredSuccess(gatt: BluetoothGatt) {
        discoverRetryCount = 0
        notifyServices(gatt.services)
        val resolved = DigitalKeyGattResolver.resolve(gatt)
        if (resolved == null) {
            scheduleDiscoverRetry(gatt, "noBbcc")
            return
        }
        lastResolved = resolved
        subscribeDigitalKeyLockNotifications(gatt, resolved)
    }

    private fun scheduleDiscoverRetry(gatt: BluetoothGatt, reason: String) {
        if (discoverRetryCount >= MAX_DISCOVER_RETRIES) {
            val msg = DigitalKeyGattResolver.userVisibleMissingMessage()
            DigitalKeyGattResolver.logGattTable(gatt.services, "exhausted retries ($reason)")
            if (sessionReachedLive) {
                notifySessionLost("discoverExhausted:$reason")
                return
            }
            setPhase(DigitalKeyConnectionPhase.SETUP_FAILED, msg)
            listeners.forEach {
                it.onDigitalKeyNotifySubscribed(false)
                it.onDigitalKeyGattUnavailable(recoverable = true, message = msg)
            }
            return
        }
        discoverRetryCount++
        setPhase(
            DigitalKeyConnectionPhase.DISCOVERING,
            getString(R.string.reflector_dashboard_discovering_retry, discoverRetryCount)
        )
        pendingRediscoverRunnable?.let { mainHandler.removeCallbacks(it) }
        val delay = 250L * discoverRetryCount
        val r = Runnable {
            pendingRediscoverRunnable = null
            if (gatt !== bluetoothGatt || !isGattClientUsable(gatt)) {
                if (gatt === bluetoothGatt) {
                    handleDeadGatt("discoverRetry:unusable")
                }
                return@Runnable
            }
            Timber.tag(TAG).i("discoverServices retry %s (%d)", reason, discoverRetryCount)
            safeDiscoverServices(gatt, "retry_$reason")
        }
        pendingRediscoverRunnable = r
        mainHandler.postDelayed(r, delay)
    }

    private var lastNotificationBuildUptime = 0L

    /** Colored padlock art for [NotificationCompat.setLargeIcon]; OEM shades often ignore [setSmallIcon] for FGS. */
    private var cachedLargeIconLocked: Bitmap? = null
    private var cachedLargeIconUnlocked: Bitmap? = null

    private fun notifyConnection(connected: Boolean) {
        listeners.forEach { it.onConnectionUiState(connected) }
    }

    private fun notifyServices(services: List<BluetoothGattService>) {
        listeners.forEach { it.onServicesDiscovered(services) }
    }

    private fun subscribeDigitalKeyLockNotifications(
        gatt: BluetoothGatt,
        resolved: DigitalKeyGattResolver.Resolved
    ) {
        if (digitalKeySubscription.isSubscribed()) {
            Timber.tag(TAG).d("DigitalKey notify already subscribed; skip duplicate CCCD")
            return
        }
        setPhase(DigitalKeyConnectionPhase.ENABLING_NOTIFICATIONS, null)
        // Reuse the existing bond; the subscription never initiates or removes pairing.
        digitalKeySubscription.beginSubscribe(gatt, resolved, delayMs = 150L)
    }

    private fun applyLockState(newState: ReflectorDigitalKeyLockState, source: String) {
        if (newState != ReflectorDigitalKeyLockState.UNKNOWN) {
            Timber.tag(TAG).i("DigitalKey [%s] -> %s", source, newState.name)
        }
        if (lockState == newState) {
            updateNotificationThrottled()
            // Re-notify so dashboard hero stays in sync when only the notification was rebuilt.
            listeners.forEach { it.onLockStateChanged(newState) }
            return
        }
        Timber.tag(TAG).i("DigitalKey lock UI: %s -> %s", lockState.name, newState.name)
        lockState = newState
        listeners.forEach { it.onLockStateChanged(newState) }
        lastNotificationBuildUptime = 0L
        updateNotificationNow()
    }

    private fun updateNotificationThrottled() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastNotificationBuildUptime < NOTIFICATION_MIN_INTERVAL_MS) return
        lastNotificationBuildUptime = now
        updateNotificationNow()
    }

    private fun updateNotificationNow() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun startAsForegroundWithType() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Left-side art in the notification shade. Many devices show the app launcher icon there unless
     * a large icon is set; [setSmallIcon] is still used for the status bar/tray glyph.
     */
    private fun digitalKeyLargeIconBitmap(keyUsable: Boolean): Bitmap {
        if (keyUsable) {
            if (cachedLargeIconUnlocked == null) {
                cachedLargeIconUnlocked = renderDrawableToBitmap(
                    R.drawable.door_unlock,
                    notificationLargeIconSizePx()
                )
            }
            return cachedLargeIconUnlocked!!
        }
        if (cachedLargeIconLocked == null) {
            cachedLargeIconLocked = renderDrawableToBitmap(
                R.drawable.door_lock,
                notificationLargeIconSizePx()
            )
        }
        return cachedLargeIconLocked!!
    }

    private fun notificationLargeIconSizePx(): Int {
        return (64f * resources.displayMetrics.density).toInt().coerceIn(64, 512)
    }

    private fun renderDrawableToBitmap(drawableRes: Int, sizePx: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(this, drawableRes)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (drawable != null) {
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
        }
        return bmp
    }

    private fun buildNotification(): Notification {
        val dashboardIntent = Intent(this, ReflectorDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReflectorDashboardActivity.EXTRA_DEVICE_ADDRESS, deviceAddress)
            putExtra(ReflectorDashboardActivity.EXTRA_DEVICE_NAME, deviceName)
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            dashboardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val connected = isGattConnected()
        val lockReportsUnlocked = lockState == ReflectorDigitalKeyLockState.UNLOCKED
        val digitalKeyUsable = connected && lockReportsUnlocked
        val reconnecting = !connected &&
            (digitalKeyPhase == DigitalKeyConnectionPhase.RECONNECTING || stackManagingReconnect)

        val titleRes = "Digital Key"
        val bodyRes = when {
            reconnecting && sessionReachedLive -> R.string.reflector_toast_auto_reconnecting
            !connected && sessionReachedLive -> R.string.reflector_toast_disconnected
            digitalKeyUsable -> R.string.reflector_fg_notification_title_unlocked
            else -> R.string.reflector_fg_notification_title_locked
        }
        val iconRes = if (digitalKeyUsable) {
            R.drawable.ic_notification_digital_key_unlocked
        } else {
            R.drawable.ic_notification_digital_key_locked
        }

        val body = getString(bodyRes)
        val largeIcon = digitalKeyLargeIconBitmap(digitalKeyUsable)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setLargeIcon(largeIcon)
            .setContentTitle(titleRes)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.reflector_fg_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.reflector_fg_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter
    }

    private fun scheduleConnectOrRefresh() {
        mainHandler.removeCallbacks(connectOrRefreshRunnable)
        mainHandler.postDelayed(connectOrRefreshRunnable, CONNECT_REFRESH_DEBOUNCE_MS)
    }

    /**
     * When connected but notify not yet enabled, refresh setup (e.g. dashboard bound mid-session).
     * Debounced and skips [discoverServices] when the service table is already cached.
     */
    private fun scheduleServiceRediscovery() {
        if (digitalKeySubscription.isSubscribed()) {
            Timber.tag(TAG).d("DigitalKey notify active; skip redundant discoverServices")
            return
        }
        pendingRediscoverRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable {
            pendingRediscoverRunnable = null
            refreshDigitalKeyGattIfNeeded("refresh")
        }
        pendingRediscoverRunnable = r
        mainHandler.postDelayed(r, SERVICE_REDISCOVER_DEBOUNCE_MS)
    }

    private fun refreshDigitalKeyGattIfNeeded(reason: String) {
        val g = bluetoothGatt ?: return
        if (!isGattClientUsable(g)) {
            handleDeadGatt("refresh:unusable")
            return
        }
        if (digitalKeySubscription.isSubscribed()) return

        val cachedServices = try {
            g.services
        } catch (e: DeadObjectException) {
            handleDeadGatt("refresh:servicesDead")
            return
        } catch (e: RemoteException) {
            handleDeadGatt("refresh:servicesDead")
            return
        }

        if (cachedServices.isNotEmpty()) {
            val resolved = lastResolved ?: DigitalKeyGattResolver.resolve(g)
            if (resolved != null) {
                lastResolved = resolved
                Timber.tag(TAG).i(
                    "DigitalKey: using cached GATT table (%s); skip discoverServices",
                    reason
                )
                setPhase(DigitalKeyConnectionPhase.DISCOVERING, null)
                subscribeDigitalKeyLockNotifications(g, resolved)
                return
            }
        }

        setPhase(DigitalKeyConnectionPhase.DISCOVERING, null)
        discoverRetryCount = 0
        Timber.tag(TAG).i("GATT connected: discoverServices() for Digital Key (%s)", reason)
        safeDiscoverServices(g, reason)
    }

    /**
     * Ensures a GATT client exists without breaking an in-flight **autoConnect** reconnect.
     * Must run on the main thread (queued via [scheduleConnectOrRefresh]).
     */
    private fun connectOrRefreshOnMain() {
        if (sessionLostNotified) return
        val addr = deviceAddress ?: return
        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Timber.tag(TAG).w("Bluetooth unavailable")
            updateNotificationNow()
            return
        }

        val g = bluetoothGatt
        if (g != null) {
            val sameDevice = addr.equals(g.device.address, ignoreCase = true)
            if (!sameDevice) {
                stackManagingReconnect = false
                releaseGatt()
            } else if (isGattConnected()) {
                if (!digitalKeySubscription.isSubscribed()) {
                    if (g != null && isGattClientUsable(g)) {
                        scheduleServiceRediscovery()
                    } else {
                        handleDeadGatt("connectOrRefresh:staleGatt")
                    }
                }
                updateNotificationNow()
                return
            } else if (stackManagingReconnect) {
                // Do not close the client — that cancels stack autoConnect. Open only if missing.
                setReconnectingPhaseIfNeeded()
                if (bluetoothGatt == null) {
                    openGattClient(autoConnect = true)
                    Timber.tag(TAG).i("connectOrRefresh: opened autoConnect client (was null)")
                }
                updateNotificationNow()
                return
            } else {
                releaseGatt()
            }
        }

        val auto = stackManagingReconnect
        if (auto) {
            setReconnectingPhaseIfNeeded()
        }
        openGattClient(autoConnect = auto)
        Timber.tag(TAG).i("openGattClient(autoConnect=$auto) for $addr")
        updateNotificationNow()
    }

    /**
     * GATT status values that usually mean the phone bond no longer matches a reset board.
     * Normal out-of-range drops typically use other codes and should keep auto-reconnect.
     */
    private fun isGattStaleBondDisconnectStatus(status: Int): Boolean = when (status) {
        5, // GATT_INSUFFICIENT_AUTHENTICATION
        15, // GATT_INSUFFICIENT_ENCRYPTION
        137, // common ATT auth / key mismatch on reconnect after peripheral reset
        -> true
        else -> false
    }

    private fun openGattClient(autoConnect: Boolean) {
        if (sessionLostNotified) return
        val addr = deviceAddress ?: return
        val device = bluetoothAdapter()?.getRemoteDevice(addr) ?: return
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(this, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(this, autoConnect, gattCallback)
        }
    }

    private fun releaseGatt() {
        gattRecoveryInProgress = false
        cancelPendingDigitalKeyGattWork()
        digitalKeySubscription.reset()
        csRangingController.stop()
        lockState = ReflectorDigitalKeyLockState.UNKNOWN
        setPhase(DigitalKeyConnectionPhase.DISCONNECTED, null)
        try {
            bluetoothGatt?.close()
        } catch (_: Exception) { }
        bluetoothGatt = null
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    companion object {
        private const val TAG = "ReflectorProximityFGS"

        const val ACTION_START = "com.siliconlabs.bledemo.reflector.action.START"
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        /** When true, clears terminal session flags even if the device address is unchanged. */
        const val EXTRA_FORCE_NEW_SESSION = "extra_force_new_session"

        private const val CHANNEL_ID = "reflector_proximity_monitoring"
        const val NOTIFICATION_ID = 90421

        private const val NOTIFICATION_MIN_INTERVAL_MS = 1_200L
        private const val MAX_DISCOVER_RETRIES = 6
        private const val CONNECT_REFRESH_DEBOUNCE_MS = 200L
        private const val SERVICE_REDISCOVER_DEBOUNCE_MS = 300L
        /** After LIVE, if the link does not restore, treat as board reset / session loss. */
        private const val SESSION_RESTORE_TIMEOUT_MS = 90_000L

        const val ACTION_STOP = "com.siliconlabs.bledemo.reflector.action.STOP"

        @JvmOverloads
        fun start(
            context: Context,
            deviceAddress: String,
            deviceName: String?,
            forceNewSession: Boolean = false,
        ) {
            val i = Intent(context, ReflectorProximityForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)
                putExtra(EXTRA_DEVICE_NAME, deviceName)
                putExtra(EXTRA_FORCE_NEW_SESSION, forceNewSession)
            }
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, ReflectorProximityForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(i)
        }
    }
}
