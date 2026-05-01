package com.siliconlabs.bledemo.home_screen.viewmodels

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.viewmodels.ScannerViewModel
import com.siliconlabs.bledemo.bluetooth.ble.BluetoothDeviceInfo
import com.siliconlabs.bledemo.bluetooth.ble.ManufacturerDataFilter
import com.siliconlabs.bledemo.bluetooth.ble.ScanResultCompat
import com.siliconlabs.bledemo.bluetooth.services.BluetoothService


class SelectDeviceViewModel : ScannerViewModel() {

    private val _scannedDevices: MutableLiveData<MutableMap<String, BluetoothDeviceInfo>> =
        MutableLiveData(mutableMapOf())
    val scannedDevices: LiveData<MutableMap<String, BluetoothDeviceInfo>> = _scannedDevices
    private val _deviceToInsert: MutableLiveData<BluetoothDeviceInfo> = MutableLiveData()
    val deviceToInsert: LiveData<BluetoothDeviceInfo> = _deviceToInsert
    private val _numberOfDevices: MutableLiveData<Int> = MutableLiveData(0)
    val numberOfDevices: LiveData<Int> = _numberOfDevices

    @SuppressLint("SuspiciousIndentation")
    override fun handleScanResult(
        result: ScanResultCompat,
        connectType: BluetoothService.GattConnectType?,
        context: Context?
    ) {
        val manufacturerDataFilter = ManufacturerDataFilter(
            id = 71,
            data = byteArrayOf(2, 0)
        )

        synchronized(_scannedDevices) {
            val deviceName = result.device?.name
            var shouldAddDevice = true
            if (deviceName == null) {
                shouldAddDevice = false
            }

            if (connectType != null && connectType == BluetoothService.GattConnectType.BLINKY) {
                if (deviceName != null) {
                    if (context != null) {
                        if (!deviceName.startsWith(
                                context.getString(R.string.blinky_service_name),
                                ignoreCase = true
                            )
                            && !matchesManufacturerData(result, manufacturerDataFilter)
                        ) {
                            shouldAddDevice = false
                        }
                    }
                }
            }

            if (connectType != null && connectType == BluetoothService.GattConnectType.THERMOMETER) {
                if (deviceName != null && context != null) {
                    if (!deviceName.startsWith("Thermometer", ignoreCase = true)
                        && !matchesManufacturerData(result, manufacturerDataFilter)
                    ) {
                        shouldAddDevice = false
                    }
                }
            }
            if (connectType != null && connectType == BluetoothService.GattConnectType.CHANNEL_SOUNDING_DEMO) {
                if (deviceName != null && context != null) {
                    if (!deviceName.startsWith("CS RFLCT", ignoreCase = true)
                        && !matchesManufacturerData(result, manufacturerDataFilter)
                    ) {
                        shouldAddDevice = false
                    }
                }
            }
            if (connectType != null && connectType == BluetoothService.GattConnectType.SMART_LOCK) {
                if (deviceName != null && context != null) {
                    if (!deviceName.startsWith("BLE_CONFIGURATOR", ignoreCase = true)
                        && !matchesManufacturerData(result, manufacturerDataFilter)
                    ) {
                        shouldAddDevice = false
                    }
                }
            }
            if (connectType != null && connectType == BluetoothService.GattConnectType.RANGE_TEST) {
                if (deviceName != null && context != null) {
                    if (!deviceName.startsWith("DMP", ignoreCase = true)
                        && !matchesManufacturerData(result, manufacturerDataFilter)
                    ) {
                        shouldAddDevice = false
                    }
                }
            }

            if (connectType != null && connectType == BluetoothService.GattConnectType.LIGHT) {
                if (deviceName != null) {
                    val matchesDMP = deviceName.startsWith("DMP", ignoreCase = true)
                    val matchesConfigurator =
                        deviceName.startsWith("BLE_CONFIGURATOR", ignoreCase = true)
                    val matchesAWS = deviceName.startsWith("BLE", ignoreCase = true) //sidewalk
                    val matchesZigbee = deviceName.startsWith("Zig", ignoreCase = true)
                    val matchesManufacturer =
                        matchesManufacturerData(result, manufacturerDataFilter)

                    if (!matchesDMP && !matchesConfigurator && !matchesAWS && !matchesZigbee
                        && !matchesManufacturer
                    ) {
                        shouldAddDevice = false
                    }
                }
            }

            if (connectType != null && connectType == BluetoothService.GattConnectType.THROUGHPUT_TEST) {
                if (deviceName != null) {
                    if (context != null) {
                        if (!deviceName.startsWith("Throughput", ignoreCase = true)
                            && !matchesManufacturerData(result, manufacturerDataFilter)
                        ) {
                            shouldAddDevice = false
                        }
                    }
                }
            }

            if (connectType != null && connectType == BluetoothService.GattConnectType.IOP_TEST) {
                if (deviceName != null) {
                    if (context != null) {
                        if (!deviceName.startsWith(
                                "IOP",
                                ignoreCase = true
                            )
                            && !matchesManufacturerData(result, manufacturerDataFilter)
                        ) {
                            shouldAddDevice = false
                        }
                    }
                }
            }

            if (connectType != null && connectType == BluetoothService.GattConnectType.MOTION) {
                if (deviceName != null) {
                    val matchesDev = deviceName.startsWith("DEV", ignoreCase = true)
                    val matchesThunder =
                        deviceName.startsWith("Thunder", ignoreCase = true)

                    val matchesManufacturer =
                        matchesManufacturerData(result, manufacturerDataFilter)

                    if (!matchesDev && !matchesThunder && !matchesManufacturer
                    ) {
                        shouldAddDevice = false
                    }
                }
            }



            if (connectType != null && connectType == BluetoothService.GattConnectType.AWS_DEMO) {
                if (deviceName != null) {
                    if (!deviceName.equals("BLE_CONFIGURATOR", ignoreCase = true)) {
                        shouldAddDevice = false
                    }
                } else {
                    shouldAddDevice = false
                }
            }

            if (connectType != null && connectType == BluetoothService.GattConnectType.WIFI_COMMISSIONING) {
                if (deviceName != null) {
                    if (!deviceName.equals("BLE_CONFIGURATOR", ignoreCase = true)) {
                        shouldAddDevice = false
                    }
                } else {
                    shouldAddDevice = false
                }
            }

            if (connectType != null && connectType == BluetoothService.GattConnectType.SMART_LOCK) {
                if (deviceName != null) {
                    if (!deviceName.equals("BLE_CONFIGURATOR", ignoreCase = true)) {
                        shouldAddDevice = false
                    }
                } else {
                    shouldAddDevice = false
                }
            }

            if (shouldAddDevice) {
                _scannedDevices.value?.apply {
                    val address = result.device?.address!!
                    val isNewDevice = !keys.contains(address)

                    getOrPut(address, { BluetoothDeviceInfo(result.device!!) }).also {
                        updateScanInfo(it, result)
                    }

                    if (isNewDevice) {
                        _deviceToInsert.value = this[address]
                        _numberOfDevices.value = size
                    }
                }
            }
            _isAnyDeviceDiscovered.value = _scannedDevices.value?.isNotEmpty() ?: false
        }


    }

    fun clearDevices() {
        _scannedDevices.postValue(mutableMapOf())
        _numberOfDevices.postValue(0)
    }

    /**
     * Sorted device list for the select-device UI. Refresh the list UI every
     * [SCANNED_DEVICE_LIST_UI_INTERVAL_MS] while scanning.
     */
    fun getScannedDevicesList(): List<BluetoothDeviceInfo> {
        return _scannedDevices.value?.values
            ?.sortedWith(compareByDescending<BluetoothDeviceInfo> { it.rssi }.thenByDescending { it.address })
            ?: listOf()
    }

    fun matchesManufacturerData(result: ScanResultCompat, filter: ManufacturerDataFilter): Boolean {
        val manufacturerData = result.scanRecord?.getManufacturerSpecificData(filter.id)
        return manufacturerData != null && manufacturerData.contentEquals(filter.data)
    }

    companion object {
        /** How often the device list UI should reload from [getScannedDevicesList]. */
        const val SCANNED_DEVICE_LIST_UI_INTERVAL_MS = 1000L
    }
}