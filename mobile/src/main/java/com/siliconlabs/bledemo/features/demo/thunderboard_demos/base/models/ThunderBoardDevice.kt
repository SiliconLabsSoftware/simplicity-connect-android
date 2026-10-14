package com.siliconlabs.bledemo.features.demo.thunderboard_demos.base.models

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile

@SuppressLint("MissingPermission")
class ThunderBoardDevice(
    device: BluetoothDevice,
    preferredName: String? = null,
) {
    /**
     * Prefer the advertisement / scan-list display name when provided.
     * [BluetoothDevice.getName] can return a stale placeholder (e.g. "Dev Kit #00000")
     * while the scan record has the correct unique name.
     */
    var name: String = when {
        !preferredName.isNullOrBlank() && preferredName != "N/A" -> preferredName
        !device.name.isNullOrBlank() -> device.name
        else -> device.address
    }
    var batteryLevel = 0
    var powerSource = PowerSource.UNKNOWN
    var modelNumber: String? = null
    var firmwareVersion: String? = null

    var state = BluetoothProfile.STATE_DISCONNECTED

    val boardType: Type
        get() = when (modelNumber) {
            THUNDERBOARD_MODEL_SENSE -> Type.THUNDERBOARD_SENSE
            THUNDERBOARD_MODEL_BLUE_V1,
            THUNDERBOARD_MODEL_BLUE_V2 -> Type.THUNDERBOARD_BLUE
            THUNDERBOARD_MODEL_DEV_KIT_V3,
            THUNDERBOARD_MODEL_DEV_KIT_V1,
            THUNDERBOARD_MODEL_DEV_KIT_V2 -> Type.THUNDERBOARD_DEV_KIT
            else -> Type.UNKNOWN
        }

    enum class PowerSource(val value: Int) {
        USB(1),
        COIN_CELL(4),
        UNKNOWN(0);

        companion object {
            fun fromInt(code: Int) : PowerSource {
                for (source in values()) {
                    if (source.value == code ) return source
                }
                return UNKNOWN
            }
        }
    }

    enum class Type {
        THUNDERBOARD_SENSE,
        THUNDERBOARD_BLUE,
        THUNDERBOARD_DEV_KIT,
        UNKNOWN
    }

    companion object {
        const val THUNDERBOARD_MODEL_SENSE = "BRD4166A"
        const val THUNDERBOARD_MODEL_BLUE_V1 = "BRD4184A"
        const val THUNDERBOARD_MODEL_BLUE_V2 = "BRD4184B"
        const val THUNDERBOARD_MODEL_DEV_KIT_V1 = "BRD2601A"
        const val THUNDERBOARD_MODEL_DEV_KIT_V2 = "BRD2601B"
        const val THUNDERBOARD_MODEL_DEV_KIT_V3 = "BRD2608A"
    }

}