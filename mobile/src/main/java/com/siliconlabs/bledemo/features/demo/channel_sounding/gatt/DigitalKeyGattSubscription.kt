package com.siliconlabs.bledemo.features.demo.channel_sounding.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.siliconlabs.bledemo.features.demo.channel_sounding.services.ReflectorDigitalKeyLockState
import com.siliconlabs.bledemo.utils.UuidConsts
import com.siliconlabs.bledemo.utils.isBluetoothUuid16
import timber.log.Timber

/**
 * Serializes Digital Key (BBCC) read → CCCD → notify on one GATT client.
 * Always pass a fresh [DigitalKeyGattResolver.Resolved] from the latest [onServicesDiscovered].
 */
@SuppressLint("MissingPermission")
class DigitalKeyGattSubscription(
    private val logTag: String = DigitalKeyGattClient.LOG_TAG,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val listener: Listener
) {
    interface Listener {
        fun onSubscribed(gatt: BluetoothGatt)
        fun onSubscribeFailed(reason: String, recoverable: Boolean)
        fun onLockState(state: ReflectorDigitalKeyLockState, source: String)
    }

    enum class Phase {
        IDLE,
        PENDING_START,
        READING_BEFORE_CCCD,
        WRITING_CCCD,
        SUBSCRIBED
    }

    private var phase: Phase = Phase.IDLE
    private var retryCount: Int = 0
    private var pendingLockChar: BluetoothGattCharacteristic? = null
    private var pendingCccd: BluetoothGattDescriptor? = null
    private var pendingRunnable: Runnable? = null

    fun isSubscribed(): Boolean = phase == Phase.SUBSCRIBED

    fun reset() {
        cancelPending()
        phase = Phase.IDLE
        retryCount = 0
        pendingLockChar = null
        pendingCccd = null
    }

    fun cancelPending() {
        pendingRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRunnable = null
    }

    /**
     * @param resolved Result from [DigitalKeyGattResolver.resolve] on the same discovery pass (required for reliability).
     */
    fun beginSubscribe(
        gatt: BluetoothGatt,
        resolved: DigitalKeyGattResolver.Resolved?,
        delayMs: Long = 200L,
        forceReadBeforeCccd: Boolean = false
    ) {
        cancelPending()
        val r = Runnable {
            pendingRunnable = null
            runSubscribeAttempt(gatt, resolved, forceReadBeforeCccd)
        }
        pendingRunnable = r
        if (delayMs <= 0L) {
            mainHandler.post(r)
        } else {
            mainHandler.postDelayed(r, delayMs)
        }
    }

    private fun runSubscribeAttempt(
        gatt: BluetoothGatt,
        resolved: DigitalKeyGattResolver.Resolved?,
        forceReadBeforeCccd: Boolean
    ) {
        if (phase == Phase.SUBSCRIBED) return

        val snapshot = resolved ?: DigitalKeyGattResolver.resolve(gatt)
        if (snapshot == null) {
            listener.onSubscribeFailed(
                DigitalKeyGattResolver.userVisibleMissingMessage(),
                recoverable = true
            )
            return
        }

        val characteristic = snapshot.lockCharacteristic
        val descriptor = snapshot.cccd

        if (!DigitalKeyGattResolver.hasNotifyProperty(characteristic)) {
            listener.onSubscribeFailed(
                "Lock characteristic 0xBBCC does not support NOTIFY on this firmware build.",
                recoverable = false
            )
            return
        }

        // Reuse the existing bond only. Never initiate pairing from the background monitor —
        // doing so triggers a re-pair prompt and rotates the IRK, which breaks auto-reconnect.
        // Android transparently re-encrypts with the stored LTK on reconnect when BONDED.
        when (gatt.device.bondState) {
            BluetoothDevice.BOND_BONDED -> { /* proceed */ }
            BluetoothDevice.BOND_BONDING -> {
                Timber.tag(logTag).i("Bonding in progress; defer Digital Key subscribe")
                phase = Phase.PENDING_START
                return
            }
            else -> {
                Timber.tag(logTag).w("Device not bonded; not re-pairing in background (reuse existing bond)")
                phase = Phase.IDLE
                listener.onSubscribeFailed(
                    "Existing pairing not found. Please re-pair with the lock.",
                    recoverable = true
                )
                return
            }
        }

        DigitalKeyGattClient.logGattSecurityContext(gatt, characteristic, descriptor)

        val readFirst = forceReadBeforeCccd ||
            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0)
        if (readFirst) {
            phase = Phase.READING_BEFORE_CCCD
            pendingLockChar = characteristic
            pendingCccd = descriptor
            Timber.tag(logTag).i("DigitalKey: read BBCC before CCCD")
            if (!gatt.readCharacteristic(characteristic)) {
                phase = Phase.IDLE
                retryOrFail(gatt, resolved, "readCharacteristic before CCCD returned false")
            }
            return
        }

        writeCccd(gatt, characteristic, descriptor)
    }

    private fun writeCccd(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        descriptor: BluetoothGattDescriptor
    ) {
        phase = Phase.WRITING_CCCD
        val useLegacy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && retryCount >= 2
        if (!DigitalKeyGattClient.writeCccdNotifyEnable(gatt, descriptor, characteristic, useLegacy)) {
            phase = Phase.IDLE
            retryOrFail(gatt, null, "CCCD write could not be queued")
        }
    }

    fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray?,
        status: Int
    ) {
        if (!DigitalKeyGattResolver.isLockStateCharacteristic(characteristic)) return

        if (phase == Phase.READING_BEFORE_CCCD) {
            val lockChar = pendingLockChar ?: characteristic
            val desc = pendingCccd
            pendingLockChar = null
            pendingCccd = null

            if (status != BluetoothGatt.GATT_SUCCESS) {
                phase = Phase.IDLE
                Timber.tag(logTag).w(
                    "BBCC read-before-CCCD failed status=%s bond=%s",
                    DigitalKeyGattClient.gattStatusName(status),
                    DigitalKeyGattClient.bondStateName(gatt.device.bondState)
                )
                // Reuse the existing bond; never re-pair here. If the peer rejected encryption
                // it will drop the link and autoConnect will retry the existing bond on reconnect.
                retryOrFail(gatt, null, "read before CCCD failed")
                return
            }
            applyPayload(value, "read-before-cccd")
            if (desc == null) {
                phase = Phase.IDLE
                retryOrFail(gatt, null, "CCCD descriptor lost")
                return
            }
            writeCccd(gatt, lockChar, desc)
            return
        }

        if (status == BluetoothGatt.GATT_SUCCESS) {
            applyPayload(value, "read")
        }
    }

    fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        if (!DigitalKeyGattResolver.isLockStateCharacteristic(descriptor.characteristic)) return
        if (descriptor.uuid != UuidConsts.CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR &&
            !isBluetoothUuid16(descriptor.uuid, 0x2902)
        ) {
            return
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            phase = Phase.IDLE
            Timber.tag(logTag).w(
                "CCCD write failed status=%s (%s) retry=%s",
                status,
                DigitalKeyGattClient.gattStatusName(status),
                retryCount
            )
            // Reuse the existing bond; never re-pair here. Let autoConnect retry on reconnect.
            val forceRead = gatt.device.bondState == BluetoothDevice.BOND_BONDED && status == 3
            retryOrFail(gatt, null, "CCCD write failed", forceReadBeforeCccd = forceRead)
            return
        }

        phase = Phase.SUBSCRIBED
        retryCount = 0
        Timber.tag(logTag).i("DigitalKey CCCD OK; subscribed")
        listener.onSubscribed(gatt)

        val lockChar = DigitalKeyGattResolver.resolve(gatt)?.lockCharacteristic
            ?: descriptor.characteristic
        if (!gatt.readCharacteristic(lockChar)) {
            Timber.tag(logTag).w("post-CCCD readCharacteristic(BBCC) returned false")
        }
    }

    fun onCharacteristicChanged(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray?
    ) {
        if (!DigitalKeyGattResolver.isLockStateCharacteristic(characteristic)) return
        applyPayload(value, "notify")
    }

    private fun applyPayload(value: ByteArray?, source: String) {
        val state = DigitalKeyGattClient.parseLockState(value) ?: run {
            Timber.tag(logTag).w("DigitalKey [%s] empty/invalid payload; ignoring", source)
            return
        }
        listener.onLockState(state, source)
    }

    private fun retryOrFail(
        gatt: BluetoothGatt,
        resolved: DigitalKeyGattResolver.Resolved?,
        reason: String,
        forceReadBeforeCccd: Boolean = true
    ) {
        if (retryCount < MAX_RETRIES) {
            retryCount++
            phase = Phase.PENDING_START
            beginSubscribe(gatt, resolved, delayMs = 400L * retryCount, forceReadBeforeCccd = forceReadBeforeCccd)
        } else {
            phase = Phase.IDLE
            listener.onSubscribeFailed(reason, recoverable = true)
        }
    }

    companion object {
        private const val MAX_RETRIES = 5
    }
}
