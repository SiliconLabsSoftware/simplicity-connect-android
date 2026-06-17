package com.siliconlabs.bledemo.bluetooth.services

import android.annotation.SuppressLint
// GATT-server debug notification (disabled) — uncomment if re-enabling:
// import android.app.Notification
// import android.app.NotificationChannel
import android.app.NotificationManager
// import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED
import android.bluetooth.le.ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED
import android.bluetooth.le.ScanCallback.SCAN_FAILED_INTERNAL_ERROR
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.RequiresApi
import androidx.core.location.LocationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.bluetooth.ble.BleScanCallback
import com.siliconlabs.bledemo.bluetooth.ble.BluetoothDeviceInfo
import com.siliconlabs.bledemo.bluetooth.ble.BluetoothScanCallback
import com.siliconlabs.bledemo.bluetooth.ble.ConnectedDeviceInfo
import com.siliconlabs.bledemo.bluetooth.ble.GattConnection
import com.siliconlabs.bledemo.bluetooth.ble.ScanResultCompat
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
// import com.siliconlabs.bledemo.features.configure.advertiser.activities.PendingServerConnectionActivity
import com.siliconlabs.bledemo.features.configure.advertiser.services.AdvertiserService
import com.siliconlabs.bledemo.features.configure.gatt_configurator.utils.BluetoothGattServicesCreator
import com.siliconlabs.bledemo.features.configure.gatt_configurator.utils.GattConfiguratorStorage
import com.siliconlabs.bledemo.features.scan.browser.models.logs.ConnectionStateChangeLog
import com.siliconlabs.bledemo.features.scan.browser.models.logs.GattOperationLog
import com.siliconlabs.bledemo.features.scan.browser.models.logs.GattOperationWithDataLog
import com.siliconlabs.bledemo.features.scan.browser.models.logs.GattOperationWithParameterLog
import com.siliconlabs.bledemo.features.scan.browser.models.logs.Log
import com.siliconlabs.bledemo.features.scan.browser.models.logs.TimeoutLog
// import com.siliconlabs.bledemo.home_screen.activities.MainActivity
import com.siliconlabs.bledemo.home_screen.activities.MainActivity.Companion.ACTION_SHOW_CUSTOM_TOAST
import com.siliconlabs.bledemo.home_screen.activities.MainActivity.Companion.EXTRA_TOAST_MESSAGE
import com.siliconlabs.bledemo.home_screen.menu_items.HealthThermometer
import com.siliconlabs.bledemo.utils.BLEUtils
import com.siliconlabs.bledemo.utils.CustomToastManager
import com.siliconlabs.bledemo.utils.LocalService
import com.siliconlabs.bledemo.utils.Notifications
import com.siliconlabs.bledemo.utils.UuidConsts
import timber.log.Timber
import java.lang.reflect.Method
import java.util.LinkedList
import java.util.UUID

/**
 * Service handling Bluetooth (regular and BLE) communications.
 */

@SuppressWarnings("LogNotTimber")
@SuppressLint("MissingPermission")
class BluetoothService : LocalService<BluetoothService>() {

    var bluetoothContext: Context? = null

    companion object {
        private const val RECONNECTION_RETRIES = 3
        private const val RECONNECTION_DELAY =
            1000L //connection drops after ~ 4s when reconnecting without delay
        private const val CONNECTION_TIMEOUT = 20000L
        private const val RSSI_UPDATE_FREQUENCY = 2000L
        private const val REFRESH_SERVICES_DELAY = 500L // give device time refresh cache

        const val ACTION_SHOW_BOND_LOSS_DIALOG =
            "com.siliconlabs.bledemo.action.SHOW_BOND_LOSS_DIALOG"
        // GATT-server debug notification (disabled) — companion symbols for restore:
        // private const val ACTION_GATT_SERVER_DEBUG_CONNECTION =
        //     "com.siliconlabs.bledemo.action.GATT_SERVER_DEBUG_CONNECTION"
        // private const val ACTION_GATT_SERVER_REMOVE_NOTIFICATION =
        //     "com.siliconlabs.bledemo.action.GATT_SERVER_REMOVE_NOTIFICATION"
        // private const val GATT_SERVER_REMOVE_NOTIFICATION_REQUEST_CODE = 666
        // private const val GATT_SERVER_DEBUG_CONNECTION_REQUEST_CODE = 888
        // private const val GATT_SERVER_OPEN_CONNECTION_REQUEST_CODE = 777
        private const val NOTIFICATION_ID = 999
        // private const val CHANNEL_ID = "DEBUG_CONNECTION_CHANNEL"
        const val EXTRA_BLUETOOTH_DEVICE = "EXTRA_BLUETOOTH_DEVICE"
        const val EXTRA_DEVICE_ADDRESS = "EXTRA_DEVICE_ADDRESS"
    }

    abstract class Binding(context: Context) : LocalService.Binding<BluetoothService>(context) {
        override fun getServiceClass(): Class<BluetoothService> {
            return BluetoothService::class.java
        }
    }

    enum class GattConnectType {
        THERMOMETER,
        LIGHT,
        RANGE_TEST,
        BLINKY,
        BLINKY_THUNDERBOARD,
        THROUGHPUT_TEST,
        WIFI_COMMISSIONING,
        MOTION,
        ENVIRONMENT,
        IOP_TEST,
        ESL_DEMO,
        MATTER_DEMO,
        WIFI_OTA_UPDATE,
        DEV_KIT_SENSOR,
        WIFI_THROUGHPUT_TEST,
        AWS_DEMO,
        WIFI_PROVISIONING,
        SMART_LOCK,
        CHANNEL_SOUNDING_DEMO,
        ENERGY_HARVESTING_DEMO,
        NOTHING
    }

    interface ScanListener {

        /**
         * Called when a new scan result has been obtained. There can be two different sources for
         * this: BleScanCallback or BluetoothScanCallback. Result is filled with informations
         * contained in those callbacks.
         *
         * @param scanResult    Data obtained through bluetooth scanner.
         */
        fun handleScanResult(scanResult: ScanResultCompat)

        /**
         * Called when scanning has ended due to system errors.
         */
        fun onDiscoveryFailed()

        /**
         * Called when scanning ended due to timeout set in app's settings
         */
        fun onDiscoveryTimeout()

    }

    private val scanReceiver: BroadcastReceiver = BluetoothScanCallback(this)
    private val scanListeners = ScanListeners()

    var servicesStateListener: ServicesStateListener? = null
    var areBluetoothPermissionsEnabled = false

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var handler: Handler

    private var useBLE = true

    var bluetoothGattServer: BluetoothGattServer? = null
        private set
    private var gattServerCallback: BluetoothGattServerCallback? = null
    private var bleScannerCallback: BleScanCallback? = null
    var bluetoothAdapter: BluetoothAdapter? = null
    var connectedGatt: BluetoothGatt? = null
        private set

    private val pendingConnections: MutableMap<String, GattConnection> = mutableMapOf()
    private val activeConnections: MutableMap<String, ConnectedDeviceInfo> = mutableMapOf()
    private var retryAttempts = 0

    private val connectionLogs: MutableList<Log> = mutableListOf()

    private var gattServerServicesToAdd: LinkedList<BluetoothGattService>? = null
    private val devicesToNotify = mutableMapOf<UUID, MutableSet<BluetoothDevice>>()
    private val devicesToIndicate = mutableMapOf<UUID, MutableSet<BluetoothDevice>>()

    var isNotificationEnabled = true

    private val rssiUpdateRunnable = object : Runnable {
        override fun run() {
            synchronized(activeConnections) {
                activeConnections.values.forEach {
                    if (it.connection.hasRssiUpdates) {
                        it.connection.gatt?.readRemoteRssi()
                    }
                }
            }
            handler.postDelayed(this, RSSI_UPDATE_FREQUENCY)
        }
    }

    private val connectionTimeoutRunnable = Runnable {
        connectedGatt?.let { gatt ->
            addDeviceLog(TimeoutLog(gatt))
            gatt.disconnect()
            reconnectionRunnable?.let { handler.removeCallbacks(it) }
            reconnectionRunnable = null
            extraGattCallback?.onTimeout()
            retryAttempts = 0
        }
    }

    private val scanTimeoutRunnable = Runnable {
        stopDiscovery()
        scanListeners.onDiscoveryTimeout()
        val intent = Intent(ACTION_SHOW_CUSTOM_TOAST).apply {
            putExtra(EXTRA_TOAST_MESSAGE, getString(R.string.toast_scan_timeout))
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private var reconnectionRunnable: ReconnectionRunnable? = null

    inner class ReconnectionRunnable(val connection: GattConnection) : Runnable {
        override fun run() {
            connection.gatt?.let {
                connectGatt(it.device, connection.hasRssiUpdates, null, true)
            }
            reconnectionRunnable = null
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()

        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            stopSelf()
            return
        }

        if (useBLE) {
            useBLE = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        }

        handler = Handler(Looper.getMainLooper())

        registerReceiver(scanReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        registerReceiver(locationReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        // GATT-server debug notification (disabled): registerGattServerReceiver()
        initGattServer()
        // Register for KEY_MISSING and ENCRYPTION_CHANGE intents
        val filter = IntentFilter().apply {
            addAction("android.bluetooth.device.action.KEY_MISSING")
            addAction("android.bluetooth.device.action.ENCRYPTION_CHANGE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(keyMissingAndEncryptionChangeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(keyMissingAndEncryptionChangeReceiver, filter)
        }

        // Register bond state receiver
        registerReceiver(bondStateReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    fun setAreBluetoothPermissionsGranted(areBluetoothPermissionsGranted: Boolean) {
        areBluetoothPermissionsEnabled = areBluetoothPermissionsGranted
        initGattServer()
    }

    interface ServicesStateListener {
        fun onBluetoothStateChanged(isOn: Boolean)
        fun onLocationStateChanged(isOn: Boolean)
        fun onNotificationStateChanged(isOn: Boolean)
    }

    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            bluetoothContext = context
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        initGattServer()
                        servicesStateListener?.onBluetoothStateChanged(true)
                    }

                    BluetoothAdapter.STATE_OFF -> {
                        /* All connections are terminated and closed already by system at this
                        point. Only clearing model is necessary for UI purposes. */
                        activeConnections.clear()
                        servicesStateListener?.onBluetoothStateChanged(false)
                    }
                }
            }
        }
    }

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val locationManager = context.getSystemService(LOCATION_SERVICE) as LocationManager
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                val state = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                servicesStateListener?.onLocationStateChanged(state)
            }
        }
    }

    fun isBluetoothOn() =
        BluetoothAdapter.getDefaultAdapter() != null && BluetoothAdapter.getDefaultAdapter().isEnabled

    fun isLocationOn(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    fun areNotificationOn(): Boolean {
        return (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
    }

    fun initGattServer() {
        if (bluetoothGattServer == null && bluetoothAdapter?.isEnabled == true && areBluetoothPermissionsEnabled) {
            bluetoothGattServer = bluetoothManager.openGattServer(this, bluetoothGattServerCallback)
            setGattServer()
        }
    }

    fun clearGattServer() {
        bluetoothGattServer?.clearServices()
        gattServerServicesToAdd?.clear()
    }

    fun setGattServer() {
        val storage = GattConfiguratorStorage(this)
        val gattServer = storage.loadActiveGattServer()
        clearGattServer()

        gattServer?.let { server ->
            gattServerServicesToAdd = BluetoothGattServicesCreator.getBluetoothGattServices(server)
            gattServerServicesToAdd?.let { services ->
                if (services.isNotEmpty()) bluetoothGattServer?.addService(services.pop())
            }
        }
    }

    // --- GATT-server debug connection notification (disabled; uncomment imports/constants, onCreate/onDestroy, and the large block before closeGattServerNotification) ---
    // private fun registerGattServerReceiver() {
    //     val filter = IntentFilter().apply {
    //         addAction(ACTION_GATT_SERVER_DEBUG_CONNECTION)
    //         addAction(ACTION_GATT_SERVER_REMOVE_NOTIFICATION)
    //     }
    //     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    //         registerReceiver(gattServerBroadcastReceiver, filter, RECEIVER_EXPORTED)
    //     } else {
    //         registerReceiver(gattServerBroadcastReceiver, filter)
    //     }
    // }

    override fun onDestroy() {
        stopDiscovery()
        disconnectAllGatts()
        AdvertiserService.stopService(applicationContext)
        try {
            unregisterReceiver(scanReceiver)
        } catch (_: IllegalArgumentException) {
        }
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: IllegalArgumentException) {
        }
        try {
            unregisterReceiver(locationReceiver)
        } catch (_: IllegalArgumentException) {
        }
        // GATT-server debug notification (disabled):
        // try { unregisterReceiver(gattServerBroadcastReceiver) } catch (_: IllegalArgumentException) {}
        try {
            unregisterReceiver(keyMissingAndEncryptionChangeReceiver)
        } catch (_: IllegalArgumentException) {
        }
        try {
            unregisterReceiver(bondStateReceiver)
        } catch (_: IllegalArgumentException) {
        }
        bluetoothGattServer?.close()

        super.onDestroy()
    }

    fun addListener(scanListener: ScanListener) {
        synchronized(scanListeners) {
            if (!scanListeners.contains(scanListener)) {
                scanListeners.add(scanListener)
            }
        }
    }

    fun removeListener(scanListener: ScanListener?) {
        synchronized(scanListeners) {
            scanListeners.remove(scanListener)
        }
    }


    /*fun startDiscovery(filters: List<ScanFilter>, timeoutInSeconds: Int? = null) {
        bluetoothAdapter?.let { adapter ->
            if (useBLE) {
                val skipFastScan = when (BLEUtils.GATT_DEVICE_SELECTED) {
                    GattConnectType.IOP_TEST,
                    GattConnectType.THERMOMETER,
                    GattConnectType.LIGHT,
                    GattConnectType.RANGE_TEST,
                    GattConnectType.BLINKY,
                    GattConnectType.THROUGHPUT_TEST,
                    GattConnectType.MOTION,
                    GattConnectType.ENVIRONMENT,
                    GattConnectType.WIFI_COMMISSIONING,
                    GattConnectType.ESL_DEMO,
                    GattConnectType.DEV_KIT_SENSOR,
                    GattConnectType.AWS_DEMO -> true
                    else -> false
                }

                if (skipFastScan) {
                    timeoutInSeconds?.let {
                        handler.postDelayed(scanTimeoutRunnable, it.toLong() * 1000)
                    }
                    bleScannerCallback = BleScanCallback(this)
                    val settings = ScanSettings.Builder()
                        .setLegacy(false)
                        .setReportDelay(getReportDelay())
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                    adapter.bluetoothLeScanner?.startScan(filters, settings, bleScannerCallback)
                        ?: onDiscoveryFailed(ScanError.LeScannerUnavailable)

                } else {
                    // --- Fast scan for 3 seconds with reportDelay = 0L to detect extended advertisers ---
                    val fastScanCallback = BleScanCallback(this)
                    val fastScanSettings = ScanSettings.Builder()
                        .setLegacy(false)
                        .setReportDelay(0L)
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                    adapter.bluetoothLeScanner?.startScan(filters, fastScanSettings, fastScanCallback)
                        ?: onDiscoveryFailed(ScanError.LeScannerUnavailable)

                    handler.postDelayed({
                        adapter.bluetoothLeScanner?.stopScan(fastScanCallback)
                        // --- Start original scan after fast scan ---
                        bleScannerCallback = BleScanCallback(this)
                        val settings = ScanSettings.Builder()
                            .setLegacy(false)
                            .setReportDelay(getReportDelay())
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .build()
                        adapter.bluetoothLeScanner?.startScan(filters, settings, bleScannerCallback)
                            ?: onDiscoveryFailed(ScanError.LeScannerUnavailable)
                        timeoutInSeconds?.let {
                            handler.postDelayed(scanTimeoutRunnable, it.toLong() * 1000)
                        }
                    }, 3000)
                }
            } else {
                if (!adapter.startDiscovery()) onDiscoveryFailed(ScanError.BluetoothAdapterUnavailable)
                else timeoutInSeconds?.let {
                    handler.postDelayed(scanTimeoutRunnable, it.toLong() * 1000)
                }
            }
        } ?: onDiscoveryFailed(ScanError.BluetoothAdapterUnavailable)
    }*/

    fun startDiscovery(filters: List<ScanFilter>, timeoutInSeconds: Int? = null) {
        // Runtime permission guard (Android 12+). Prevent SecurityException and provide user feedback.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                onDiscoveryFailed(ScanError.LeScannerUnavailable)
                Timber.w("startDiscovery aborted: BLUETOOTH_SCAN permission not granted")
                return
            }
        }
         val skipFastScan = when (BLEUtils.GATT_DEVICE_SELECTED) {
            GattConnectType.IOP_TEST,
            GattConnectType.THERMOMETER,
            GattConnectType.LIGHT,
            GattConnectType.RANGE_TEST,
            GattConnectType.BLINKY,
            GattConnectType.THROUGHPUT_TEST,
            GattConnectType.MOTION,
            GattConnectType.ENVIRONMENT,
            GattConnectType.WIFI_COMMISSIONING,
            GattConnectType.ESL_DEMO,
            GattConnectType.DEV_KIT_SENSOR,
            GattConnectType.AWS_DEMO,
            GattConnectType.SMART_LOCK,
            GattConnectType.CHANNEL_SOUNDING_DEMO -> true

            else -> false
        }
        if (skipFastScan) {
            startDiscoveryForDemoTiles(filters, timeoutInSeconds)
        } else {
            bluetoothAdapter?.let { adapter ->
                if (useBLE) {
                    // --- Fast scan for 3 seconds with reportDelay = 0L to detect extended advertisers ---
                    val fastScanCallback = BleScanCallback(this)
                    val fastScanSettings = ScanSettings.Builder()
                        .setLegacy(false)
                        .setReportDelay(0L)
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                    adapter.bluetoothLeScanner?.startScan(
                        filters,
                        fastScanSettings,
                        fastScanCallback
                    )
                        ?: onDiscoveryFailed(ScanError.LeScannerUnavailable)

                    handler.postDelayed({
                        adapter.bluetoothLeScanner?.stopScan(fastScanCallback)
                        // --- Start original scan after fast scan ---
                        bleScannerCallback = BleScanCallback(this)
                        val settings = ScanSettings.Builder()
                            .setLegacy(false)
                            .setReportDelay(0)
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .build()
                        adapter.bluetoothLeScanner?.startScan(filters, settings, bleScannerCallback)
                            ?: onDiscoveryFailed(ScanError.LeScannerUnavailable)
                        timeoutInSeconds?.let {
                            handler.postDelayed(scanTimeoutRunnable, it.toLong() * 1000)
                        }
                    }, 3000)
                } else {
                    if (!adapter.startDiscovery()) onDiscoveryFailed(ScanError.BluetoothAdapterUnavailable)
                    else timeoutInSeconds?.let {
                        handler.postDelayed(scanTimeoutRunnable, it.toLong() * 1000)
                    }
                }
            } ?: onDiscoveryFailed(ScanError.BluetoothAdapterUnavailable)
        }

    }

    private fun startDiscoveryForDemoTiles(
        filters: List<ScanFilter>,
        timeoutInSeconds: Int? = null
    ) {
        timeoutInSeconds?.let {
            handler.postDelayed(scanTimeoutRunnable, it.toLong() * 1000)
        }
        bluetoothAdapter?.let { adapter ->
            if (useBLE) {
                bleScannerCallback = BleScanCallback(this)
                val settings = ScanSettings.Builder()
                    .setLegacy(false)
                    .setReportDelay(getReportDelay())
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                adapter.bluetoothLeScanner?.startScan(filters, settings, bleScannerCallback)
                    ?: onDiscoveryFailed(ScanError.LeScannerUnavailable)
            } else {
                if (!adapter.startDiscovery()) onDiscoveryFailed(ScanError.BluetoothAdapterUnavailable)
                else timeoutInSeconds?.let {
                    handler.postDelayed(scanTimeoutRunnable, it.toLong() * 1000)
                }
            }
        } ?: onDiscoveryFailed(ScanError.BluetoothAdapterUnavailable)
    }

    /**
     * Determines the report delay for BLE scanning based on the GATT connection type.
     *
     * The report delay is set to 0 milliseconds for specific GATT connection types,
     * including:
     * - IOP_TEST
     * - THERMOMETER
     * - LIGHT
     * - RANGE_TEST
     * - BLINKY
     * - THROUGHPUT_TEST
     * - MOTION
     * - ENVIRONMENT
     * - WIFI_COMMISSIONING
     * - ESL_DEMO
     * - DEV_KIT_SENSOR
     *
     * For all other GATT connection types, the report delay is set to 1000 milliseconds.
     *
     * @return The report delay in milliseconds.
     */
    fun getReportDelay(): Long {
        return when (BLEUtils.GATT_DEVICE_SELECTED) {
            GattConnectType.IOP_TEST,
            GattConnectType.THERMOMETER,
            GattConnectType.LIGHT,
            GattConnectType.RANGE_TEST,
            GattConnectType.BLINKY,
            GattConnectType.THROUGHPUT_TEST,
            GattConnectType.MOTION,
            GattConnectType.ENVIRONMENT,
            GattConnectType.WIFI_COMMISSIONING,
            GattConnectType.ESL_DEMO,
            GattConnectType.DEV_KIT_SENSOR,
            GattConnectType.AWS_DEMO,
            GattConnectType.SMART_LOCK,
            GattConnectType.CHANNEL_SOUNDING_DEMO -> 0L

            else -> 1000L
        }
    }

    fun onDiscoveryFailed(scanError: ScanError, errorCode: Int? = null) {
        handler.removeCallbacks(scanTimeoutRunnable)
        val message = when (scanError) {
            ScanError.ScannerError -> when (errorCode) {
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> getString(R.string.scan_failed_application_registration_failed)
                SCAN_FAILED_FEATURE_UNSUPPORTED -> getString(R.string.scan_failed_feature_unsupported)
                SCAN_FAILED_INTERNAL_ERROR -> getString(R.string.scan_failed_internal_error)
                else -> getString(R.string.scan_failed_error_code, errorCode)
            }

            ScanError.LeScannerUnavailable -> getString(R.string.scan_failed_le_scanner_unavailable)
            ScanError.BluetoothAdapterUnavailable -> getString(R.string.scan_failed_bluetooth_adapter_unavailable)
        }
        scanListeners.onDiscoveryFailed()
        //Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        val intent = Intent(ACTION_SHOW_CUSTOM_TOAST).apply {
            putExtra(EXTRA_TOAST_MESSAGE, message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun stopDiscovery() {
        handler.removeCallbacks(scanTimeoutRunnable)
        bluetoothAdapter?.let {
            if (useBLE) {
                it.bluetoothLeScanner?.stopScan(bleScannerCallback as ScanCallback?)
                bleScannerCallback = null // Clear BLE scanner callback after stopping scan
            } else {
                if (it.isDiscovering) it.cancelDiscovery()
                else { /* added to satisfy lambda */
                }
            }
        }
    }

    fun handleScanCallback(result: ScanResultCompat) {
        scanListeners.handleScanResult(result)
    }

    enum class ScanError {
        ScannerError,
        LeScannerUnavailable,
        BluetoothAdapterUnavailable,
    }

    fun refreshGattServices(gatt: BluetoothGatt?) {
        gatt?.let {
            refreshDeviceCache(it)
            handler.postDelayed({
                it.discoverServices()
            }, REFRESH_SERVICES_DELAY)
        }
    }

    private fun refreshDeviceCache(gatt: BluetoothGatt): Boolean {
        try {
            Timber.d("refreshDevice: Called")
            val localMethod: Method = gatt.javaClass.getMethod("refresh")
            val bool: Boolean = (localMethod.invoke(gatt, *arrayOfNulls(0)) as Boolean)
            Timber.d("refreshDevice: bool: $bool")
            return bool
        } catch (localException: Exception) {
            Timber.e("refreshDevice: An exception occurred while refreshing device")
        }
        return false
    }

    fun clearConnectedGatt() {
        connectedGatt?.let { disconnectGatt(it.device.address) }
    }

    fun registerGattCallback(requestRssiUpdates: Boolean, callback: TimeoutGattCallback?) {
        handler.removeCallbacks(rssiUpdateRunnable)
        if (requestRssiUpdates) {
            handler.post(rssiUpdateRunnable)
        }
        extraGattCallback = callback
    }

    fun isGattConnected(): Boolean {
        return connectedGatt?.let {
            activeConnections.containsKey(it.device?.address!!)
                    && bluetoothManager.getConnectionState(
                it.device, BluetoothProfile.GATT
            ) == BluetoothProfile.STATE_CONNECTED
        } ?: false
    }

    fun isGattConnected(deviceAddress: String?): Boolean {
        return deviceAddress?.let {
            activeConnections.containsKey(deviceAddress)
                    && bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
                .any { it.address == deviceAddress }
        } ?: false
    }

    fun getConnectedGatt(deviceAddress: String?): BluetoothGatt? {
        deviceAddress?.let {
            connectedGatt = getActiveConnection(it)?.connection?.gatt
        }
        return connectedGatt
    }

    fun connectGatt(
        device: BluetoothDevice,
        requestRssiUpdates: Boolean,
        extraCallback: TimeoutGattCallback? = null,
        isConnectionRetry: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Timber.w("connectGatt aborted: BLUETOOTH_CONNECT permission not granted for device ${device.address}")
                extraCallback?.onConnectionStateChange(connectedGatt, BluetoothGatt.GATT_FAILURE, BluetoothProfile.STATE_DISCONNECTED)
                return
            }
        }
         stopDiscovery()
         extraCallback?.let { extraGattCallback = it }

         if (!isConnectionRetry) handler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT)

         /* Invokes onConnectionStateChange() callback */
        try {
            connectedGatt = if (useBLE) {
                device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(this, false, gattCallback)
            }
        } catch (se: SecurityException) {
            Timber.e(se, "SecurityException while connecting GATT to ${device.address}")
            extraCallback?.onConnectionStateChange(connectedGatt, BluetoothGatt.GATT_FAILURE, BluetoothProfile.STATE_DISCONNECTED)
            return
        }
         connectedGatt?.let { addPendingConnection(GattConnection(it, requestRssiUpdates)) }
     }

    fun disconnectGatt(deviceAddress: String) {
        getActiveConnection(deviceAddress)?.let {
            /* Invokes onConnectionStateChange() callback */
            it.connection.gatt?.disconnect()
        }
    }

    fun disconnectAllGatts() {
        activeConnections.values.forEach { disconnectGatt(it.connection.address) }
    }

    private fun clearGattConnection(address: String) {
        getActiveConnection(address)?.let {
            it.connection.gatt?.close()
            removeActiveConnection(it.connection.gatt?.device?.address)
            if (connectedGatt?.device?.address == address) {
                connectedGatt = null
            }
        }
        if (activeConnections.isEmpty()) handler.removeCallbacks(rssiUpdateRunnable)
    }

    private fun addPendingConnection(connection: GattConnection) {
        pendingConnections.apply {
            synchronized(this) { put(connection.address, connection) }
        }
    }

    private fun removePendingConnection(address: String) {
        pendingConnections.apply {
            synchronized(this) { remove(address) }
        }
    }

    fun isAnyConnectionPending() = pendingConnections.isNotEmpty()

    fun updateConnectionInfo(info: BluetoothDeviceInfo) {
        activeConnections[info.address]?.bluetoothInfo = info
    }

    fun updateConnectionRssi(gatt: BluetoothGatt, rssi: Int) {
        activeConnections.values.apply {
            synchronized(this) {
                val connectedDevice =
                    find { it.connection.gatt!!.device.address == gatt.device.address }
                connectedDevice?.bluetoothInfo?.rssi = rssi
            }
        }
    }

    private fun addActiveConnection(connection: GattConnection) {
        activeConnections.apply {
            synchronized(this) { put(connection.address, ConnectedDeviceInfo(connection)) }
        }
    }

    private fun removeActiveConnection(address: String?) {
        address?.let {
            activeConnections.apply {
                synchronized(this) { remove(it) }
            }
        }
    }

    private fun handleReconnection(gatt: BluetoothGatt) {
        pendingConnections[gatt.device.address]?.let {
            gatt.close()
            removePendingConnection(gatt.device.address)
            retryAttempts++

            if (retryAttempts < RECONNECTION_RETRIES) {
                reconnectionRunnable = ReconnectionRunnable(it)
                handler.postDelayed(reconnectionRunnable!!, RECONNECTION_DELAY)
            }
        }
    }

    fun getActiveConnection(address: String) = activeConnections[address]
    fun getActiveConnections() = activeConnections.values.toList()

    fun addDeviceLog(log: Log) {
        synchronized(connectionLogs) {
            connectionLogs.add(log)
        }
    }

    fun getLogsForDevice(address: String): List<Log> {
        return synchronized(connectionLogs) {
            connectionLogs.filter { it.deviceAddress == address }
        }
    }

    fun clearLogsForDevice(address: String) {
        return synchronized(connectionLogs) {
            connectionLogs.removeIf { it.deviceAddress == address }
        }
    }

    private var extraGattCallback: TimeoutGattCallback? = null

    fun registerGattCallback(callback: TimeoutGattCallback) {
        extraGattCallback = callback
    }

    fun unregisterGattCallback() {
        extraGattCallback = null
    }

    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Timber.d("onConnectionStateChange(): gatt device = ${gatt.device.address}, status = $status, newState = $newState")
            addDeviceLog(ConnectionStateChangeLog(gatt, status, newState))

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (activeConnections.isEmpty()) handler.post(rssiUpdateRunnable)

                        handler.removeCallbacks(connectionTimeoutRunnable)
                        pendingConnections[gatt.device.address]?.let { addActiveConnection(it) }
                        removePendingConnection(gatt.device.address)
                        retryAttempts = 0
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED ->
                    when (status) {
                        133 -> handleReconnection(gatt)
                        else -> clearGattConnection(gatt.device.address)
                    }
            }

            if (retryAttempts < RECONNECTION_RETRIES) {
                extraGattCallback?.onConnectionStateChange(gatt, status, newState)
            } else {
                extraGattCallback?.onMaxRetriesExceeded(gatt)
                retryAttempts = 0
                handler.removeCallbacks(connectionTimeoutRunnable)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Timber.d("onServicesDiscovered(): gatt device = ${gatt.device.address}, status = $status")
            addDeviceLog(
                GattOperationWithParameterLog(
                    gatt, GattOperationLog.Type.SERVICES_DISCOVERED,
                    status
                )
            )
            //gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            extraGattCallback?.onServicesDiscovered(gatt, status)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            Timber.d(
                "onCharacteristicRead(): gatt device = ${gatt.device.address}, uuid = ${
                    characteristic.uuid
                }, value = ${characteristic.value?.contentToString()}"
            )
            val safeValue = characteristic.value ?: ByteArray(0)
            addDeviceLog(
                GattOperationWithDataLog(
                    gatt, GattOperationLog.Type.READ_CHARACTERISTIC,
                    status, characteristic.uuid, safeValue
                )
            )
            extraGattCallback?.onCharacteristicRead(gatt, characteristic, status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Timber.d(
                "onCharacteristicWrite(): gatt device = ${gatt.device.address}, uuid = ${
                    characteristic.uuid
                }, status = $status, value = ${characteristic.value?.contentToString()}"
            )
            addDeviceLog(
                GattOperationWithDataLog(
                    gatt, GattOperationLog.Type.WRITE_CHARACTERISTIC,
                    status, characteristic.uuid, characteristic.value
                )
            )
            extraGattCallback?.onCharacteristicWrite(gatt, characteristic, status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            Timber.d(
                "onCharacteristicChanged(): gatt device = ${gatt.device.address}, uuid = ${
                    characteristic.uuid
                }, value = ${characteristic.value?.contentToString()}"
            )
            addDeviceLog(
                GattOperationWithDataLog(
                    gatt, GattOperationLog.Type.CHARACTERISTIC_CHANGED,
                    null, characteristic.uuid, characteristic.value
                )
            )
            extraGattCallback?.onCharacteristicChanged(gatt, characteristic)
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorRead(gatt, descriptor, status)
            Timber.d(
                "onDescriptorRead(): gatt device = ${gatt.device.address}, uuid = ${
                    descriptor.uuid
                }, descriptor's characteristic = ${
                    descriptor.characteristic.uuid
                }, value = ${descriptor.value?.contentToString()}"
            )
            addDeviceLog(
                GattOperationWithDataLog(
                    gatt, GattOperationLog.Type.READ_DESCRIPTOR,
                    status, descriptor.uuid, descriptor.value
                )
            )
            extraGattCallback?.onDescriptorRead(gatt, descriptor, status)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            Timber.d(
                "onDescriptorWrite(): gatt device = ${gatt.device.address}, uuid = ${
                    descriptor.uuid
                }, descriptor's characteristic = ${
                    descriptor.characteristic.uuid
                }, value = ${descriptor.value?.contentToString()}"
            )
            addDeviceLog(
                GattOperationWithDataLog(
                    gatt, GattOperationLog.Type.WRITE_DESCRIPTOR,
                    status, descriptor.uuid, descriptor.value
                )
            )
            extraGattCallback?.onDescriptorWrite(gatt, descriptor, status)
        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
            super.onReliableWriteCompleted(gatt, status)
            Timber.d("onReliableWriteCompleted(): gatt device = ${gatt.device.address}, status = $status")
            addDeviceLog(
                GattOperationWithParameterLog(
                    gatt, GattOperationLog.Type.RELIABLE_WRITE_COMPLETED,
                    status
                )
            )
            extraGattCallback?.onReliableWriteCompleted(gatt, status)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            super.onReadRemoteRssi(gatt, rssi, status)
            Timber.d("onReadRemoteRssi(): gatt device = ${gatt.device.address}, rssi = $rssi, status = $status")
            addDeviceLog(
                GattOperationWithParameterLog(
                    gatt, GattOperationLog.Type.READ_RSSI,
                    status, "rssi = $rssi"
                )
            )
            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateConnectionRssi(gatt, rssi)
                extraGattCallback?.onReadRemoteRssi(gatt, rssi, status)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("onMtuChanged(): gatt device =${gatt.device.address}, mtu = $mtu")
                addDeviceLog(
                    GattOperationWithParameterLog(
                        gatt, GattOperationLog.Type.MTU_CHANGED,
                        status, "mtu = $mtu"
                    )
                )
                extraGattCallback?.onMtuChanged(gatt, mtu, status)
            }

        }

        override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status)
            Timber.d(
                "onPhyUpdate(): gatt device = ${gatt.device?.address}, txPhy = ${
                    txPhy
                }, rxPhy = $rxPhy, status = $status"
            )
            addDeviceLog(
                GattOperationWithParameterLog(
                    gatt, GattOperationLog.Type.PHY_UPDATED,
                    status, "txPhy = $txPhy, rxPhy = $rxPhy"
                )
            )
            extraGattCallback?.onPhyUpdate(gatt, txPhy, rxPhy, status)
        }
    }

    fun unregisterGattServerCallback() {
        gattServerCallback = null
    }

    fun registerGattServerCallback(callback: BluetoothGattServerCallback) {
        this.gattServerCallback = callback
    }

    private val bluetoothGattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("BinaryOperationInTimber")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            Timber.d(
                "onServerConnectionStateChange(): device = ${device.address}," +
                        "status = $status, newState = $newState"
            )

            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> if (status == BluetoothGatt.GATT_SUCCESS) {
                    // GATT-server debug notification disabled; previous behavior:
                    // if (gattServerCallback != null) {
                    //     gattServerCallback?.onConnectionStateChange(device, status, newState)
                    // } else if (isNotificationEnabled) showDebugConnectionNotification(device)
                    gattServerCallback?.onConnectionStateChange(device, status, newState)
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            super.onServiceAdded(status, service)
            gattServerServicesToAdd?.let { services ->
                if (services.isNotEmpty()) bluetoothGattServer?.addService(services.pop())
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            bluetoothGattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                characteristic?.value
            )
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor?
        ) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            bluetoothGattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                descriptor?.value
            )
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(
                device,
                requestId,
                characteristic,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            Timber.i("onCharacteristicWriteRequest")
            characteristic?.value = value
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
                )
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onDescriptorWriteRequest(
                device,
                requestId,
                descriptor,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            Timber.i("onDescriptorWriteRequest")
            descriptor?.value = value
            if (
                descriptor != null && device != null && value != null
                && descriptor.uuid == UuidConsts.CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR
            ) {
                val characteristicUuid = descriptor.characteristic.uuid

                when (value.asList()) {
                    Notifications.DISABLED.descriptorValue.asList() -> {
                        devicesToNotify[characteristicUuid]?.remove(device)
                        devicesToIndicate[characteristicUuid]?.remove(device)
                    }

                    Notifications.NOTIFY.descriptorValue.asList() -> {
                        devicesToNotify
                            .getOrPut(characteristicUuid) { mutableSetOf() }
                            .add(device)
                        devicesToIndicate[characteristicUuid]?.remove(device)
                    }

                    Notifications.INDICATE.descriptorValue.asList() -> {
                        devicesToIndicate
                            .getOrPut(characteristicUuid) { mutableSetOf() }
                            .add(device)
                        devicesToNotify[characteristicUuid]?.remove(device)
                    }
                }
            }
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
                )
            }
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            super.onNotificationSent(device, status)
            gattServerCallback?.onNotificationSent(device, status)
        }
    }

    fun getClientsToNotify(characteristicUuid: UUID): Collection<BluetoothDevice> {
        return devicesToNotify[characteristicUuid] ?: emptySet()
    }

    fun getClientsToIndicate(characteristicUuid: UUID): Collection<BluetoothDevice> {
        return devicesToIndicate[characteristicUuid] ?: emptySet()
    }

    // private fun getYesPendingIntent(device: BluetoothDevice): PendingIntent {
    //     val intent = Intent(ACTION_GATT_SERVER_DEBUG_CONNECTION)
    //     intent.putExtra(EXTRA_BLUETOOTH_DEVICE, device)
    //     val pendingIntentFlag =
    //         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE
    //         else PendingIntent.FLAG_CANCEL_CURRENT
    //     return PendingIntent.getBroadcast(
    //         this,
    //         GATT_SERVER_DEBUG_CONNECTION_REQUEST_CODE,
    //         intent,
    //         pendingIntentFlag
    //     )
    // }
    //
    // private fun getNoPendingIntent(): PendingIntent {
    //     val intent = Intent(ACTION_GATT_SERVER_REMOVE_NOTIFICATION)
    //     val pendingIntentFlag =
    //         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE
    //         else PendingIntent.FLAG_CANCEL_CURRENT
    //     return PendingIntent.getBroadcast(
    //         this,
    //         GATT_SERVER_REMOVE_NOTIFICATION_REQUEST_CODE,
    //         intent,
    //         pendingIntentFlag
    //     )
    // }
    //
    // private fun getYesAndOpenPendingIntent(device: BluetoothDevice): PendingIntent {
    //     val intent = Intent(this, PendingServerConnectionActivity::class.java).apply {
    //         putExtra(EXTRA_BLUETOOTH_DEVICE, device)
    //         flags = Intent.FLAG_ACTIVITY_NO_HISTORY
    //     }
    //     val backIntent = Intent(this, MainActivity::class.java).apply {
    //         addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    //     }
    //     val pendingIntentFlag =
    //         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE
    //         else PendingIntent.FLAG_ONE_SHOT
    //     return PendingIntent.getActivities(
    //         this,
    //         GATT_SERVER_OPEN_CONNECTION_REQUEST_CODE,
    //         arrayOf(backIntent, intent),
    //         pendingIntentFlag
    //     )
    // }
    //
    // private fun showDebugConnectionNotification(device: BluetoothDevice) {
    //     val deviceName = device.name ?: getString(R.string.not_advertising_shortcut)
    //     createNotificationChannel()
    //     val notification = Notification.Builder(this, CHANNEL_ID)
    //         .setSmallIcon(R.mipmap.si_launcher)
    //         .setContentTitle(
    //             getString(R.string.notification_title_device_has_connected, deviceName)
    //         )
    //         .setContentText(getString(R.string.notification_note_debug_connection))
    //         .addAction(buildAction(getString(R.string.button_yes), getYesPendingIntent(device)))
    //         // .addAction(buildAction(getString(R.string.notification_button_yes_and_open), getYesAndOpenPendingIntent(device)))
    //         .addAction(buildAction(getString(R.string.button_no), getNoPendingIntent()))
    //         .build()
    //     val notificationManager =
    //         getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    //     notificationManager.notify(NOTIFICATION_ID, notification)
    // }
    //
    // private fun createNotificationChannel() {
    //     val name = getString(R.string.notification_channel_name)
    //     val descriptionText = getString(R.string.notification_channel_description)
    //     val importance = NotificationManager.IMPORTANCE_HIGH
    //     val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
    //         description = descriptionText
    //     }
    //     val notificationManager: NotificationManager =
    //         getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    //     notificationManager.createNotificationChannel(channel)
    // }
    //
    // private fun buildAction(actionText: String, actionIntent: PendingIntent): Notification.Action {
    //     return Notification.Action.Builder(
    //         R.mipmap.si_launcher,
    //         actionText,
    //         actionIntent
    //     ).build()
    // }
    //
    // private val gattServerBroadcastReceiver = object : BroadcastReceiver() {
    //     override fun onReceive(context: Context?, intent: Intent?) {
    //         when (intent?.action) {
    //             ACTION_GATT_SERVER_DEBUG_CONNECTION -> {
    //                 val device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_BLUETOOTH_DEVICE)
    //                 device?.let { connectGatt(it, false, null) }
    //                 closeGattServerNotification()
    //             }
    //             ACTION_GATT_SERVER_REMOVE_NOTIFICATION -> closeGattServerNotification()
    //         }
    //     }
    // }

    fun closeGattServerNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private class ScanListeners : ArrayList<ScanListener>(), ScanListener {

        override fun handleScanResult(scanResult: ScanResultCompat) {
            forEach { it.handleScanResult(scanResult) }
        }

        override fun onDiscoveryFailed() {
            forEach { it.onDiscoveryFailed() }
        }

        override fun onDiscoveryTimeout() {
            forEach { it.onDiscoveryTimeout() }
        }
    }

    private val keyMissingAndEncryptionChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_KEY_MISSING -> {
                    // Inform user and guide to re-pair
                    val message = getString(R.string.bluetooth_key_missing)
                    val toastIntent = Intent(ACTION_SHOW_CUSTOM_TOAST).apply {
                        putExtra(EXTRA_TOAST_MESSAGE, message)
                    }
                    LocalBroadcastManager.getInstance(this@BluetoothService)
                        .sendBroadcast(toastIntent)
                    // Disconnect all active GATT connections
                    disconnectAllGatts()
                    // Launch Bluetooth settings to prompt user to re-pair
                    val settingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(settingsIntent)
                }

                BluetoothDevice.ACTION_ENCRYPTION_CHANGE -> {
                    // Optionally handle encryption state change
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val encrypted: Boolean =
                        intent.getBooleanExtra("android.bluetooth.device.extra.ENCRYPTED", false)

                    val message = if (encrypted) {
                        getString(R.string.bluetooth_encryption_enabled, device?.name ?: "Device")
                    } else {
                        getString(R.string.bluetooth_encryption_disabled, device?.name ?: "Device")
                    }
                    val toastIntent = Intent(ACTION_SHOW_CUSTOM_TOAST).apply {
                        putExtra(EXTRA_TOAST_MESSAGE, message)
                    }
                    LocalBroadcastManager.getInstance(this@BluetoothService)
                        .sendBroadcast(toastIntent)
                }
            }
        }
    }

    /**
     * Removes the Bluetooth bond for a companion device using the new Android 16+ API.
     * @param associationId The association ID for the companion device.
     */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    fun removeBluetoothBond(associationId: Int) {
        val cdm = getSystemService(CompanionDeviceManager::class.java)
        cdm?.removeBond(associationId)
    }

    /**
     * Finds the association ID for a Bluetooth device address, if associated.
     * @param deviceAddress The Bluetooth device MAC address.
     * @return The association ID, or null if not found.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getAssociationIdForDevice(deviceAddress: String): Int? {
        val cdm = getSystemService(CompanionDeviceManager::class.java)
        if (cdm != null) {
            val associations = cdm.myAssociations
            for (info in associations) {
                if (info.deviceMacAddress?.toString()
                        ?.equals(deviceAddress, ignoreCase = true) == true
                ) {
                    return info.id
                }
            }
        }
        return null
    }

    // Register a receiver to monitor bond state changes
    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == intent?.action) {
                val reason = intent?.getIntExtra("android.bluetooth.device.extra.REASON", -1) ?: -1
                // Debug: reason can help diagnose OEM-specific bond loss causes
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                val prevBondState =
                    intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                // val reason = intent.getIntExtra("android.bluetooth.device.extra.REASON", -1) // Not used
                if (bondState == BluetoothDevice.BOND_NONE && prevBondState == BluetoothDevice.BOND_BONDED) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        // Custom bond loss handling for Android < 16
                        device?.let {
                            disconnectGatt(it.address)
                        }
                        val dialogIntent = Intent(ACTION_SHOW_BOND_LOSS_DIALOG)
                        dialogIntent.putExtra(
                            EXTRA_TOAST_MESSAGE,
                            getString(R.string.bluetooth_bond_lost) + " " + (device?.name
                                ?: "Device")
                        )
                        // dialogIntent.putExtra(EXTRA_DEVICE_ADDRESS, device?.address)
                        LocalBroadcastManager.getInstance(this@BluetoothService)
                            .sendBroadcast(dialogIntent)
                    }
                    // On Android 16+, let the system handle bond loss natively
                }
            }
        }
    }
}
