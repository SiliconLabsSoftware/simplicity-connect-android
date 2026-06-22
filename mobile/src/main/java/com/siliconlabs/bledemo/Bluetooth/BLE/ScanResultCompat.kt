package com.siliconlabs.bledemo.bluetooth.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import com.siliconlabs.bledemo.bluetooth.parsing.ScanRecordParser
import com.siliconlabs.bledemo.utils.Objects.toString
import java.util.*

/**
 * Represents a compatible version of [ScanResult] from Lollipop or higher.
 */
class ScanResultCompat {
    var device: BluetoothDevice? = null
    var rssi = 0
    var scanRecord: ScanRecordCompat? = null
    var timestampNanos: Long = 0
    var advertData: ArrayList<String?> = arrayListOf()

    var isConnectable = false
    var isLegacy = true

    // Data for no legacy devices -- Bluetooth 5 advertising extension --
    var dataStatus = 0
    var primaryPhy = 0
    var secondaryPhy = 0
    var advertisingSetID = 0
    var txPower = 0
    var periodicAdvertisingInterval = 0

    @SuppressLint("MissingPermission")
    fun getDisplayName(): String {
        // Priority: scan record device name > BluetoothDevice.name > fallback
        return scanRecord?.deviceName
            ?: device?.name
            ?: "N/A"
    }

    override fun toString(): String {
        return ("ScanResult{" + "device=" + device + ", scanRecord="
                + toString(scanRecord) + ", rssi=" + rssi + ", timestampNanos="
                + timestampNanos + '}')
    }

    companion object {
        fun from(scanResult: ScanResult): ScanResultCompat {

            return ScanResultCompat().apply {
                device = scanResult.device
                rssi = scanResult.rssi
                scanRecord = ScanRecordCompat.from(scanResult.scanRecord)
                advertData = ScanRecordParser.getAdvertisements(scanResult.scanRecord?.bytes)
                timestampNanos = scanResult.timestampNanos

                isConnectable = scanResult.isConnectable
                isLegacy = scanResult.isLegacy
                dataStatus = scanResult.dataStatus
                primaryPhy = scanResult.primaryPhy
                secondaryPhy = scanResult.secondaryPhy
                advertisingSetID = scanResult.advertisingSid
                txPower = scanResult.txPower
                periodicAdvertisingInterval = scanResult.periodicAdvertisingInterval
            }
        }
    }
}
