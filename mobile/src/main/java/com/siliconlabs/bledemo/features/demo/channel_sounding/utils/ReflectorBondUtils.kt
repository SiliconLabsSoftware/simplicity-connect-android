package com.siliconlabs.bledemo.features.demo.channel_sounding.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

/**
 * Checks whether the saved reflector lock is still bonded at the OS level.
 */
object ReflectorBondUtils {

    @SuppressLint("MissingPermission")
    fun isDeviceBonded(deviceAddress: String): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        val bonded = adapter.bondedDevices ?: return false
        return bonded.any { it.address.equals(deviceAddress, ignoreCase = true) }
    }

    @SuppressLint("MissingPermission")
    fun findBondedDevice(deviceAddress: String): BluetoothDevice? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        return adapter.bondedDevices?.firstOrNull {
            it.address.equals(deviceAddress, ignoreCase = true)
        }
    }

    /**
     * Removes the OS bond for [deviceAddress] without UI (reflection API used elsewhere in the app).
     * @return true when [removeBond] was invoked on a bonded device; false if not bonded or on failure.
     */
    @SuppressLint("MissingPermission")
    fun removeBondSilently(deviceAddress: String): Boolean {
        val device = findBondedDevice(deviceAddress) ?: return false
        if (device.bondState != BluetoothDevice.BOND_BONDED) return false
        return try {
            device::class.java.getMethod(REMOVE_BOND_METHOD).invoke(device) as Boolean
        } catch (_: Exception) {
            false
        }
    }

    private const val REMOVE_BOND_METHOD = "removeBond"
}
