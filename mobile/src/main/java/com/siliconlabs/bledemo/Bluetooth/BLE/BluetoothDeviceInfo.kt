package com.siliconlabs.bledemo.bluetooth.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.core.util.isEmpty
import com.siliconlabs.bledemo.bluetooth.beacon_utils.BleFormat
import com.siliconlabs.bledemo.bluetooth.beacon_utils.BleFormat.Companion.getFormat
import kotlin.math.min

class BluetoothDeviceInfo(var device: BluetoothDevice, var isFavorite: Boolean = false) :
    Cloneable {

    var connectionState = ConnectionState.DISCONNECTED
    var isConnectable = false

    var bleFormat: BleFormat? = null
    var scanInfo: ScanResultCompat? = null
    var rawData: String? = null

    var intervalNanos = 0L
    var count = 0
    var timestampLast: Long = 0


    public override fun clone(): BluetoothDeviceInfo {
        val retVal: BluetoothDeviceInfo
        try {
            retVal = super.clone() as BluetoothDeviceInfo
            retVal.device = device
            retVal.scanInfo = scanInfo
            retVal.bleFormat = bleFormat
            retVal.connectionState = connectionState
            retVal.isConnectable = isConnectable
            retVal.isFavorite = isFavorite
            retVal.rawData = rawData
            return retVal
        } catch (e: CloneNotSupportedException) {
            Log.e("clone", "Could not clone$e")
        }
        return BluetoothDeviceInfo(device)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is BluetoothDeviceInfo) {
            return false
        }
        return device == other.device
    }

    override fun hashCode(): Int {
        return device.hashCode()
    }

    override fun toString(): String {
        return scanInfo.toString()
    }

    fun getBleFormat(shouldCheckAgain: Boolean): BleFormat {
        if (bleFormat == null || shouldCheckAgain) {
            // device can be programmed to advertise changing data, switching between BLE formats
            bleFormat = getFormat(this)
        }
        return bleFormat!!
    }

    fun setIntervalIfLower(intervalNanos: Long) {
        if (intervalNanos <= 0L) return
        if (this.intervalNanos == 0L) this.intervalNanos = intervalNanos
        else if (intervalNanos < this.intervalNanos * 0.7 && count < 10)
            this.intervalNanos = intervalNanos
        else if (intervalNanos < this.intervalNanos + 3000000) {
            val limitedCount = min(count, 10)
            this.intervalNanos =
                (this.intervalNanos * (limitedCount - 1) + intervalNanos) / limitedCount
        } else if (intervalNanos < this.intervalNanos * 1.4) {
            this.intervalNanos = (this.intervalNanos * 29 + intervalNanos) / 30
        }
    }


    val advertData: ArrayList<String?>
        get() = scanInfo?.advertData ?: arrayListOf()

    var rssi: Int
        get() = scanInfo?.rssi ?: 0
        set(rssi) {
            scanInfo?.rssi = rssi
        }

    /**
     * Returns the best available display name for the device.
     * Priority:
     * 1. Complete / Short Local Name from current advertisement (scan record parsed)
     * 2. BluetoothDevice.name (may be null on first sightings or if name not cached)
     * 3. "N/A" fallback
     */
    val name: String
        @SuppressLint("MissingPermission")
        get() = scanInfo?.scanRecord?.deviceName
            ?: device.name
            ?: "N/A"

    val address: String
        get() = device.address

    val manufacturer: DeviceManufacturer
        get() = scanInfo?.scanRecord?.manufacturerSpecificData?.let {
            println("---------------MAnufacture:$it")
            if (it.isEmpty()) DeviceManufacturer.UNKNOWN
            else when (it.keyAt(0)) {
                MANUFACTURER_VALUE_WINDOWS -> DeviceManufacturer.WINDOWS
                else -> DeviceManufacturer.UNKNOWN
            }
        } ?: DeviceManufacturer.UNKNOWN

    enum class DeviceManufacturer {
        WINDOWS,
        ANDROID,
        UNKNOWN
    }

    enum class ConnectionState {
        CONNECTED,
        CONNECTING,
        DISCONNECTED
    }

    companion object {
        private const val MANUFACTURER_VALUE_WINDOWS = 6
    }

}