package com.siliconlabs.bledemo.features.demo.channel_sounding.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.os.Build
import com.siliconlabs.bledemo.features.demo.channel_sounding.services.ReflectorDigitalKeyLockState
import com.siliconlabs.bledemo.utils.UuidConsts
import com.siliconlabs.bledemo.utils.isBluetoothUuid16
import timber.log.Timber
import java.util.ArrayDeque
import java.util.IdentityHashMap

/**
 * Minimal GATT client helpers for the CS Initiator lock contract (service 0xAABB,
 * characteristic 0xBBCC read+notify, CCCD 0x2902). Used by
 * [com.siliconlabs.bledemo.features.demo.channel_sounding.services.ReflectorProximityForegroundService].
 */
@SuppressLint("MissingPermission")
object DigitalKeyGattClient {

    const val LOG_TAG = "DigitalKeyGatt"

    /**
     * Flatten primary and included services (some stacks expose AABB/BBCC only under included services).
     */
    fun flattenServices(root: Collection<BluetoothGattService>): List<BluetoothGattService> {
        val result = mutableListOf<BluetoothGattService>()
        val queue = ArrayDeque<BluetoothGattService>()
        val seen = IdentityHashMap<BluetoothGattService, Boolean>()
        root.forEach { queue.add(it) }
        while (queue.isNotEmpty()) {
            val s = queue.removeFirst()
            if (seen.containsKey(s)) continue
            seen[s] = true
            result.add(s)
            s.includedServices?.forEach { queue.add(it) }
        }
        return result
    }

    /**
     * Prefer BBCC under service 0xAABB; otherwise first BBCC in the flattened table.
     */
    fun findLockCharacteristic(services: List<BluetoothGattService>): BluetoothGattCharacteristic? {
        val flat = flattenServices(services)
        val underService = flat
            .firstOrNull {
                isBluetoothUuid16(it.uuid, UuidConsts.DIGITAL_KEY_SERVICE_UUID16) ||
                    it.uuid == UuidConsts.DIGITAL_KEY_SERVICE
            }
            ?.characteristics
            ?.firstOrNull { isLockStateCharacteristic(it) }
        if (underService != null) {
            Timber.tag(LOG_TAG).i("Digital Key: found BBCC under AABB service")
            return underService
        }
        val any = flat.asSequence()
            .flatMap { it.characteristics.asSequence() }
            .firstOrNull { isLockStateCharacteristic(it) }
        if (any != null) {
            Timber.tag(LOG_TAG).w("Digital Key: found BBCC but no AABB parent in flattened GATT")
        } else {
            Timber.tag(LOG_TAG).w("Digital Key: BBCC not present after discovery")
        }
        return any
    }

    fun hasDigitalKeyGatt(services: List<BluetoothGattService>): Boolean =
        findLockCharacteristic(services) != null

    fun isLockStateCharacteristic(c: BluetoothGattCharacteristic): Boolean =
        isBluetoothUuid16(c.uuid, UuidConsts.DIGITAL_KEY_LOCK_CHARACTERISTIC_UUID16) ||
            c.uuid == UuidConsts.DIGITAL_KEY_LOCK_STATE

    fun findCccd(characteristic: BluetoothGattCharacteristic): BluetoothGattDescriptor? {
        characteristic.getDescriptor(UuidConsts.CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR)?.let { return it }
        return characteristic.descriptors.firstOrNull {
            it.uuid == UuidConsts.CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR ||
                isBluetoothUuid16(it.uuid, 0x2902)
        }
    }

    fun bondStateName(state: Int): String = when (state) {
        BluetoothDevice.BOND_NONE -> "BOND_NONE"
        BluetoothDevice.BOND_BONDING -> "BOND_BONDING"
        BluetoothDevice.BOND_BONDED -> "BOND_BONDED"
        else -> "BOND_$state"
    }

    /** Logs bond + ATT permissions to diagnose CCCD status 3 / 5 / 15. */
    fun logGattSecurityContext(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        descriptor: BluetoothGattDescriptor?
    ) {
        val bond = gatt.device.bondState
        val props = characteristic.properties
        val perms = characteristic.permissions
        val descPerms = descriptor?.permissions ?: 0
        Timber.tag(LOG_TAG).i(
            "GATT security: bond=%s (%s) BBCC props=0x%04x perms=0x%02x CCCD perms=0x%02x",
            bond,
            bondStateName(bond),
            props,
            perms,
            descPerms
        )
    }

    /**
     * Enable notifications on BBCC: [setCharacteristicNotification] then write CCCD with
     * [BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE] (0x0001).
     *
     * On API 33+ prefer the two-arg [BluetoothGatt.writeDescriptor]; some stacks still need the
     * legacy single-arg path ([useLegacyWrite]).
     *
     * @return true if the stack accepted the write request (GATT_SUCCESS at initiation on API 33+).
     */
    fun writeCccdNotifyEnable(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        characteristic: BluetoothGattCharacteristic,
        useLegacyWrite: Boolean = false
    ): Boolean {
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            Timber.tag(LOG_TAG).w("setCharacteristicNotification(BBCC) returned false before CCCD")
            return false
        }
        val cccdBytes = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !useLegacyWrite) {
            val result = gatt.writeDescriptor(descriptor, cccdBytes)
            val ok = result == BluetoothGatt.GATT_SUCCESS
            Timber.tag(LOG_TAG).i(
                "CCCD writeDescriptor(API>=33) init=%s (%s) queued=%s",
                result,
                gattStatusName(result),
                ok
            )
            ok
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = cccdBytes
            @Suppress("DEPRECATION")
            val queued = gatt.writeDescriptor(descriptor)
            Timber.tag(LOG_TAG).i(
                "CCCD writeDescriptor(legacy) queued=%s api=%s",
                queued,
                Build.VERSION.SDK_INT
            )
            queued
        }
    }

    fun gattStatusName(status: Int): String = when (status) {
        BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS"
        1 -> "GATT_INVALID_HANDLE"
        2 -> "GATT_READ_NOT_PERMITTED"
        3 -> "GATT_WRITE_NOT_PERMITTED"
        5 -> "GATT_INSUFFICIENT_AUTHENTICATION"
        6 -> "GATT_REQUEST_NOT_SUPPORTED"
        7 -> "GATT_INVALID_OFFSET"
        13 -> "GATT_INVALID_ATTRIBUTE_LENGTH"
        15 -> "GATT_INSUFFICIENT_ENCRYPTION"
        22 -> "GATT_CONNECTION_CONGESTED"
        133 -> "GATT_ERROR"
        257 -> "GATT_FAILURE"
        else -> "GATT_STATUS_$status"
    }

    fun isAuthError(status: Int): Boolean = status == 5 || status == 15

    /**
     * Returns first byte for length >= 1; logs if length != 1 per contract.
     */
    fun parseLockPayload(value: ByteArray?): Byte? {
        if (value == null || value.isEmpty()) return null
        if (value.size != 1) {
            Timber.tag(LOG_TAG).w("Lock payload length=%d (expected 1); using value[0]", value.size)
        }
        return value[0]
    }

    /** Maps contract byte to lock state; returns null for invalid payloads. */
    fun parseLockState(value: ByteArray?): ReflectorDigitalKeyLockState? {
        val b = parseLockPayload(value) ?: return null
        return when (b.toInt() and 0xFF) {
            0x00 -> ReflectorDigitalKeyLockState.UNLOCKED
            0x01 -> ReflectorDigitalKeyLockState.LOCKED
            else -> {
                Timber.tag(LOG_TAG).w("Unexpected lock byte 0x%02X", b.toInt() and 0xFF)
                null
            }
        }
    }
}
