package com.siliconlabs.bledemo.features.demo.channel_sounding.services

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.ranging.RangingDevice
import android.ranging.RangingData
import android.ranging.RangingManager
import android.ranging.RangingPreference
import android.ranging.RangingSession
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.siliconlabs.bledemo.features.demo.channel_sounding.interfaces.BleConnection
import com.siliconlabs.bledemo.features.demo.channel_sounding.utils.ChannelSoundingConfigureParameters
import com.siliconlabs.bledemo.features.demo.channel_sounding.utils.ChannelSoundingRangingParameters
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Keeps an Android BLE Channel Sounding **responder** session alive so the initiator firmware
 * can run CS procedures and update the Digital Key lock characteristic.
 *
 * Requires API [Build.VERSION_CODES.BAKLAVA] and [android.permission.RANGING].
 */
@SuppressLint("MissingPermission")
class ReflectorCsRangingController(
    private val context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val logTag: String = TAG,
    private val onRunningChanged: ((Boolean) -> Unit)? = null
) {
    private val noopBleConnection = object : BleConnection {}

    private var rangingManager: RangingManager? = null
    private var session: RangingSession? = null
    private var cancellationSignal: CancellationSignal? = null
    private var targetDevice: BluetoothDevice? = null
    private var enabled: Boolean = true
    private var running: Boolean = false

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            stop()
        }
    }

    fun isRunning(): Boolean = running

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    fun startIfPossible(device: BluetoothDevice) {
        if (!enabled) {
            Timber.tag(logTag).d("CS reflector disabled; not starting ranging")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Timber.tag(logTag).w("CS reflector requires API %d+", Build.VERSION_CODES.BAKLAVA)
            return
        }
        if (!hasPermission(Manifest.permission.RANGING)) {
            Timber.tag(logTag).e("Missing RANGING permission — CS reflector cannot start")
            return
        }
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            Timber.tag(logTag).w("Device not bonded — defer CS reflector")
            return
        }
        if (running && targetDevice?.address == device.address) {
            return
        }
        stop()
        targetDevice = device
        startSession(device)
    }

    fun stop() {
        if (!running && session == null) return
        Timber.tag(logTag).i("Stopping CS reflector ranging session")
        try {
            cancellationSignal?.cancel()
        } catch (_: Exception) { }
        cancellationSignal = null
        session = null
        setRunning(false)
    }

    private fun setRunning(active: Boolean) {
        if (running == active) return
        running = active
        mainHandler.post { onRunningChanged?.invoke(active) }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun startSession(device: BluetoothDevice) {
        val mgr = context.getSystemService(RangingManager::class.java) as? RangingManager
        if (mgr == null) {
            Timber.tag(logTag).e("RangingManager not available")
            return
        }
        rangingManager = mgr

        val executor = Executors.newSingleThreadExecutor()
        session = mgr.createRangingSession(executor, sessionCallback)

        val config = ChannelSoundingConfigureParameters.restoreInstance(context.applicationContext, true)
        val preference = ChannelSoundingRangingParameters.createResponderRangingPreference(
            context.applicationContext,
            noopBleConnection,
            ChannelSoundingRangingParameters.Technology.BLE_CS.toString(),
            ChannelSoundingRangingParameters.Freq.HIGH.toString(),
            config,
            0,
            device
        )
        if (preference == null) {
            Timber.tag(logTag).e("Failed to build CS responder RangingPreference")
            return
        }

        executor.execute {
            try {
                cancellationSignal = session?.start(preference)
                Timber.tag(logTag).i("CS reflector ranging session start requested for %s", device.address)
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "CS reflector session start failed")
                mainHandler.post { setRunning(false) }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private val sessionCallback = object : RangingSession.Callback {
        override fun onOpened() {
            Timber.tag(logTag).i("CS reflector ranging session opened")
        }

        override fun onOpenFailed(reason: Int) {
            setRunning(false)
            Timber.tag(logTag).e("CS reflector ranging onOpenFailed reason=%s", reason)
        }

        override fun onClosed(reason: Int) {
            setRunning(false)
            Timber.tag(logTag).w("CS reflector ranging onClosed reason=%s", reason)
        }

        override fun onStarted(peer: RangingDevice, technology: Int) {
            setRunning(true)
            Timber.tag(logTag).i("CS reflector onStarted technology=%s", technology)
        }

        override fun onStopped(peer: RangingDevice, technology: Int) {
            setRunning(false)
            Timber.tag(logTag).w("CS reflector onStopped technology=%s", technology)
        }

        override fun onResults(peer: RangingDevice, data: RangingData) {
            Timber.tag(logTag).v("CS reflector onResults peer=%s", peer)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "ReflectorCsRanging"
    }
}
