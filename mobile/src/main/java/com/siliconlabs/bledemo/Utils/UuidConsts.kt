package com.siliconlabs.bledemo.utils

import java.util.Locale
import java.util.UUID

object UuidConsts {
    val OTA_SERVICE: UUID = UUID.fromString("1d14d6ee-fd63-4fa1-bfa4-8f47b42119f0")
    val OTA_CONTROL: UUID = UUID.fromString("f7bf3564-fb6d-4e53-88a4-5e37e0326063")
    val OTA_DATA: UUID = UUID.fromString("984227f3-34fc-4045-a5d0-2c581f81a153")

    val GENERIC_ACCESS: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
    val DEVICE_NAME: UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")

    val CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** CS Initiator lock-state service (16-bit 0xAABB, Bluetooth SIG base UUID). */
    val DIGITAL_KEY_SERVICE: UUID = UUID.fromString("0000aabb-0000-1000-8000-00805f9b34fb")

    /** Lock state characteristic: read + notify, 1 byte (0x00 unlock, 0x01 lock). */
    val DIGITAL_KEY_LOCK_STATE: UUID = UUID.fromString("0000bbcc-0000-1000-8000-00805f9b34fb")

    const val DIGITAL_KEY_SERVICE_UUID16: Int = 0xAABB
    const val DIGITAL_KEY_LOCK_CHARACTERISTIC_UUID16: Int = 0xBBCC
}

/** SIG Bluetooth base UUID for 16-bit assigned numbers (e.g. AABB, BBCC, 2902). */
fun bluetoothUuidFrom16Bits(shortUuid16: Int): UUID {
    return UUID.fromString(
        String.format(Locale.US, "0000%04x-0000-1000-8000-00805f9b34fb", shortUuid16 and 0xFFFF)
    )
}

fun isBluetoothUuid16(uuid: UUID, shortUuid16: Int): Boolean {
    return uuid == bluetoothUuidFrom16Bits(shortUuid16)
}
