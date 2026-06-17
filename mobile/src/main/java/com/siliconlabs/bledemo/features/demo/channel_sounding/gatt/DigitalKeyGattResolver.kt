package com.siliconlabs.bledemo.features.demo.channel_sounding.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.siliconlabs.bledemo.utils.UuidConsts
import com.siliconlabs.bledemo.utils.isBluetoothUuid16
import timber.log.Timber
import java.util.UUID

/**
 * Resolves Digital Key GATT attributes after [BluetoothGatt.discoverServices] completes.
 * Uses [BluetoothGatt.getService] first (stable on Android), then flattened fallback.
 */
@SuppressLint("MissingPermission")
object DigitalKeyGattResolver {

    const val LOG_TAG = DigitalKeyGattClient.LOG_TAG

    data class Resolved(
        val lockCharacteristic: BluetoothGattCharacteristic,
        val cccd: BluetoothGattDescriptor,
        val serviceUuid: UUID
    )

    /**
     * @return [Resolved] or null if lock notify characteristic is not yet visible on this GATT table.
     */
    fun resolve(gatt: BluetoothGatt): Resolved? {
        if (gatt.services.isNullOrEmpty()) {
            Timber.tag(LOG_TAG).w("resolve: gatt.services empty — discovery not finished")
            return null
        }

        resolveViaGetService(gatt)?.let { return it }

        val lock = DigitalKeyGattClient.findLockCharacteristic(gatt.services)
        if (lock == null) {
            logGattTable(gatt.services, "BBCC not found after discovery")
            return null
        }
        val cccd = DigitalKeyGattClient.findCccd(lock)
        if (cccd == null) {
            Timber.tag(LOG_TAG).e("BBCC found but CCCD 0x2902 missing on %s", lock.uuid)
            return null
        }
        val serviceUuid = findParentServiceUuid(gatt.services, lock) ?: UuidConsts.DIGITAL_KEY_SERVICE
        logResolved(lock, cccd, serviceUuid, viaGetService = false)
        return Resolved(lock, cccd, serviceUuid)
    }

    fun hasNotifyProperty(characteristic: BluetoothGattCharacteristic): Boolean =
        (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0

    fun isLockStateCharacteristic(c: BluetoothGattCharacteristic): Boolean =
        DigitalKeyGattClient.isLockStateCharacteristic(c)

    private fun resolveViaGetService(gatt: BluetoothGatt): Resolved? {
        val service = gatt.getService(UuidConsts.DIGITAL_KEY_SERVICE)
            ?: gatt.services.firstOrNull {
                isBluetoothUuid16(it.uuid, UuidConsts.DIGITAL_KEY_SERVICE_UUID16) ||
                    it.uuid == UuidConsts.DIGITAL_KEY_SERVICE
            }
            ?: return null

        val lock = service.getCharacteristic(UuidConsts.DIGITAL_KEY_LOCK_STATE)
            ?: service.characteristics?.firstOrNull { isLockStateCharacteristic(it) }
            ?: return null

        val cccd = DigitalKeyGattClient.findCccd(lock) ?: return null
        logResolved(lock, cccd, service.uuid, viaGetService = true)
        return Resolved(lock, cccd, service.uuid)
    }

    private fun findParentServiceUuid(
        services: List<BluetoothGattService>,
        characteristic: BluetoothGattCharacteristic
    ): UUID? {
        val flat = DigitalKeyGattClient.flattenServices(services)
        return flat.firstOrNull { svc ->
            svc.characteristics?.any { it.uuid == characteristic.uuid } == true
        }?.uuid
    }

    private fun logResolved(
        lock: BluetoothGattCharacteristic,
        cccd: BluetoothGattDescriptor,
        serviceUuid: UUID,
        viaGetService: Boolean
    ) {
        Timber.tag(LOG_TAG).i(
            "Digital Key resolved (%s): service=%s char=%s props=0x%x",
            if (viaGetService) "getService" else "flatten",
            serviceUuid,
            lock.uuid,
            lock.properties
        )
    }

    fun logGattTable(services: List<BluetoothGattService>, reason: String) {
        val flat = DigitalKeyGattClient.flattenServices(services)
        val summary = flat.joinToString { s ->
            val chars = s.characteristics?.joinToString { c ->
                c.uuid.toString().substring(4, 8).lowercase()
            } ?: ""
            "${s.uuid.toString().substring(4, 8).lowercase()}[$chars]"
        }
        Timber.tag(LOG_TAG).e("%s — GATT table: %s", reason, summary)
    }

    /** User-visible detail when BBCC is missing (maps to "not exposed" reports). */
    fun userVisibleMissingMessage(): String =
        "Lock characteristic 0xBBCC is not exposed on this connection. " +
            "Retry discovery or re-pair with the door lock firmware."
}
